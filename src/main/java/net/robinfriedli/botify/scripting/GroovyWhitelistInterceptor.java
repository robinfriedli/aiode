package net.robinfriedli.botify.scripting;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.robinfriedli.botify.util.ClassDescriptorNode;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Conditions;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;

import static net.robinfriedli.botify.util.ClassDescriptorNode.*;
import static net.robinfriedli.jxp.queries.Conditions.*;

public class GroovyWhitelistInterceptor extends GroovyInterceptor {

    private final Map<Class<?>, WhitelistedClassContribution> allowedClasses;
    private final Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods;
    private final Map<Class<?>, Set<String>> writeableProperties;

    public GroovyWhitelistInterceptor(Map<Class<?>, WhitelistedClassContribution> allowedClasses, Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods, Map<Class<?>, Set<String>> writeableProperties) {
        this.allowedClasses = allowedClasses;
        this.allowedMethods = allowedMethods;
        this.writeableProperties = writeableProperties;
    }

    public static GroovyWhitelistInterceptor createFromConfiguration(Context configuration) {
        Map<Class<?>, WhitelistedClassContribution> allowedClasses = configuration.query(tagName("whitelistClass"))
            .getResultStream()
            .map(elem -> {
                try {
                    return new WhitelistedClassContribution(Class.forName(elem.getAttribute("class").getValue()), elem.getAttribute("maxMethodInvocations").getInt());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Invalid whitelist configuration, invalid class encountered", e);
                }
            })
            .collect(Collectors.toMap(WhitelistedClassContribution::getType, contribution -> contribution));

        Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods = new HashMap<>();
        Map<Class<?>, Set<String>> writeableProperties = new HashMap<>();
        for (XmlElement methodWhitelist : configuration.query(Conditions.tagName("whitelistMethods")).collect()) {
            Class<?> allowedClass;
            try {
                allowedClass = Class.forName(methodWhitelist.getAttribute("class").getValue());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Invalid whitelist configuration, invalid  class encountered", e);
            }

            WhitelistedClassContribution allowedClassContribution = new WhitelistedClassContribution(allowedClass, methodWhitelist.getAttribute("maxMethodInvocations").getInt());
            Set<WhitelistedMethodContribution> methodNames = methodWhitelist
                .query(tagName("method"))
                .getResultStream()
                .map(elem -> new WhitelistedMethodContribution(elem.getAttribute("name").getValue(), elem.getAttribute("maxInvocationCount").getInt(), allowedClassContribution))
                .collect(Collectors.toSet());

            Set<String> writeablePropertyNames = methodWhitelist
                .query(tagName("writeProperty"))
                .getResultStream()
                .map(elem -> elem.getAttribute("name").getValue())
                .collect(Collectors.toSet());

            allowedMethods.put(allowedClass, methodNames);
            writeableProperties.put(allowedClass, writeablePropertyNames);
        }

        return new GroovyWhitelistInterceptor(allowedClasses, allowedMethods, writeableProperties);
    }

    @Override
    public Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args) throws Throwable {
        Class<?> receiverClass = receiver.getClass();
        checkMethodCall(receiverClass, method);
        return super.onMethodCall(invoker, receiver, method, args);
    }

    @Override
    public Object onStaticCall(Invoker invoker, Class receiver, String method, Object... args) throws Throwable {
        checkMethodCall(receiver, method);
        return super.onStaticCall(invoker, receiver, method, args);
    }

    @Override
    public Object onSetProperty(Invoker invoker, Object receiver, String property, Object value) throws Throwable {
        Class<?> type = receiver.getClass();
        WhitelistedClassContribution allowedClasses = findAllowedClasses(type);
        if (allowedClasses == null) {
            Set<String> writeableProperties = this.writeableProperties.get(type);
            if (writeableProperties == null || !writeableProperties.contains(property)) {
                String propertyDisplay = type.getName() + "#" + property;
                throw new SecurityException(String.format("Cannot reassign property %s. Property does not exist or is not writeable.", propertyDisplay));
            }
        }

        return super.onSetProperty(invoker, receiver, property, value);
    }

    public void resetInvocationCount() {
        allowedClasses.values().forEach(WhitelistedClassContribution::resetCurrentInvocationCount);
        allowedMethods.values().stream().flatMap(Collection::stream).forEach(WhitelistedMethodContribution::resetCurrentInvocationCount);
    }

    private void checkMethodCall(Class<?> type, String method) {
        WhitelistedClassContribution allowedClassContribution = findAllowedClasses(type);
        if (allowedClassContribution != null) {
            allowedClassContribution.incrementMethodInvocationCount();
            if (allowedClassContribution.getMaxMethodInvocations() > 0 && allowedClassContribution.getCurrentMethodInvocationCount() > allowedClassContribution.getMaxMethodInvocations()) {
                throw new SecurityException(String.format("The maximum method invocation count of %s for %s reached", allowedClassContribution.getMaxMethodInvocations(), type));
            }
        } else {
            WhitelistedMethodContribution methodContribution = findAllowedMethods(type, method);
            String methodDisplay = type.getName() + "#" + method;
            if (methodContribution == null) {
                throw new SecurityException("Method does not exist or is not allowed: " + methodDisplay);
            }

            WhitelistedClassContribution classContribution = methodContribution.getClassContribution();
            classContribution.incrementMethodInvocationCount();

            if (classContribution.getMaxMethodInvocations() > 0 && classContribution.getCurrentMethodInvocationCount() > classContribution.getMaxMethodInvocations()) {
                throw new SecurityException(String.format("The maximum method invocation count of %s for %s reached", classContribution.getMaxMethodInvocations(), type));
            }

            methodContribution.incrementInvocationCount();
            if (methodContribution.getMaxInvocationCount() > 0 && methodContribution.getCurrentInvocationCount() > methodContribution.getMaxInvocationCount()) {
                throw new SecurityException(String.format("Method '%s' may only be invoked %s times per script execution thread", methodDisplay, methodContribution.getMaxInvocationCount()));
            }
        }
    }

    private WhitelistedClassContribution findAllowedClasses(Class<?> declarationClass) {
        Set<WhitelistedClassContribution> foundContributions = allowedClasses.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(declarationClass))
            .map(Map.Entry::getValue)
            .collect(Collectors.toSet());

        return selectClosestNode(foundContributions, declarationClass);
    }

    private WhitelistedMethodContribution findAllowedMethods(Class<?> declaringClass, String method) {
        List<WhitelistedMethodContribution> methodContributions = allowedMethods.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(declaringClass))
            .flatMap(entry -> entry.getValue().stream())
            .filter(contribution -> contribution.getMethod().equals(method))
            .collect(Collectors.toList());

        return selectClosestNode(methodContributions, declaringClass);
    }

    private static class WhitelistedClassContribution implements ClassDescriptorNode {

        private final Class<?> type;
        private final int maxMethodInvocations;
        private final ThreadLocal<Integer> currentInvocationCount;

        public WhitelistedClassContribution(Class<?> type, int maxMethodInvocations) {
            this.type = type;
            this.maxMethodInvocations = maxMethodInvocations;
            currentInvocationCount = ThreadLocal.withInitial(() -> 0);
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        public int getMaxMethodInvocations() {
            return maxMethodInvocations;
        }

        public int getCurrentMethodInvocationCount() {
            return currentInvocationCount.get();
        }

        public void resetCurrentInvocationCount() {
            currentInvocationCount.set(0);
        }

        public void incrementMethodInvocationCount() {
            currentInvocationCount.set(getCurrentMethodInvocationCount() + 1);
        }
    }

    private static class WhitelistedMethodContribution implements ClassDescriptorNode {

        private final String method;
        private final int maxInvocationCount;
        private final ThreadLocal<Integer> currentInvocationCount;
        private final WhitelistedClassContribution classContribution;

        public WhitelistedMethodContribution(String method, int maxInvocationCount, WhitelistedClassContribution classContribution) {
            this.method = method;
            this.maxInvocationCount = maxInvocationCount;
            currentInvocationCount = ThreadLocal.withInitial(() -> 0);
            this.classContribution = classContribution;
        }

        public String getMethod() {
            return method;
        }

        /**
         * @return the maximum amount of times this method may be invoked, 0 means there is no limit
         */
        public int getMaxInvocationCount() {
            return maxInvocationCount;
        }

        public int getCurrentInvocationCount() {
            return currentInvocationCount.get();
        }

        public void resetCurrentInvocationCount() {
            classContribution.resetCurrentInvocationCount();
            currentInvocationCount.set(0);
        }

        public void incrementInvocationCount() {
            currentInvocationCount.set(getCurrentInvocationCount() + 1);
        }

        public WhitelistedClassContribution getClassContribution() {
            return classContribution;
        }

        @Override
        public Class<?> getType() {
            return classContribution.getType();
        }
    }
}

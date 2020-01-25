package net.robinfriedli.botify.scripting

import net.robinfriedli.jxp.api.XmlElement
import net.robinfriedli.jxp.persist.Context
import org.kohsuke.groovy.sandbox.GroovyInterceptor

import java.util.stream.Collectors

import static net.robinfriedli.jxp.queries.Conditions.tagName

class GroovyWhitelistInterceptor extends GroovyInterceptor {

    private final Map<Class<?>, WhitelistedClassContribution> allowedClasses
    private final Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods

    GroovyWhitelistInterceptor(Map<Class<?>, WhitelistedClassContribution> allowedClasses, Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods) {
        this.allowedClasses = allowedClasses
        this.allowedMethods = allowedMethods
    }

    static GroovyWhitelistInterceptor createFromConfiguration(Context configuration) {
        Map<Class<?>, WhitelistedClassContribution> allowedClasses = configuration.query(tagName("whitelistAllMethods"))
            .getResultStream()
            .map({ elem -> new WhitelistedClassContribution(Class.forName(elem.getAttribute("class").getValue()), elem.getAttribute("maxMethodInvocations").getInt()) })
            .collect(Collectors.toMap({ contribution -> contribution.getType() }, { contribution -> contribution }))

        Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods = new HashMap<>()
        for (XmlElement methodWhitelist : configuration.query(tagName("whitelistMethods")).collect()) {
            Class<?> allowedClass = Class.forName(methodWhitelist.getAttribute("class").getValue())
            WhitelistedClassContribution allowedClassContribution = new WhitelistedClassContribution(allowedClass, methodWhitelist.getAttribute("maxMethodInvocations").getInt())
            Set<WhitelistedMethodContribution> methodNames = methodWhitelist
                .query(tagName("method"))
                .getResultStream()
                .map({ elem -> new WhitelistedMethodContribution(elem.getAttribute("name").getValue(), elem.getAttribute("maxInvocationCount").getInt(), allowedClassContribution) })
                .collect(Collectors.toSet())

            allowedMethods.put(allowedClass, methodNames)
        }

        return new GroovyWhitelistInterceptor(allowedClasses, allowedMethods)
    }

    @Override
    Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args) throws Throwable {
        Class<?> receiverClass = receiver.getClass()
        checkMethodCall(receiverClass, method)
        return super.onMethodCall(invoker, receiver, method, args)
    }

    @Override
    Object onStaticCall(Invoker invoker, Class receiver, String method, Object... args) throws Throwable {
        checkMethodCall(receiver, method)
        return super.onStaticCall(invoker, receiver, method, args)
    }

    void checkMethodCall(Class<?> type, String method) {
        def allowedClassContribution = findAllowedClasses(type)
        if (allowedClassContribution != null) {
            allowedClassContribution.incrementMethodInvocationCount()
            if (allowedClassContribution.getMaxMethodInvocations() > 0 && allowedClassContribution.getCurrentMethodInvocationCount() > allowedClassContribution.getMaxMethodInvocations()) {
                throw new SecurityException(String.format("The maximum method invocation count of %s for %s reached", allowedClassContribution.getMaxMethodInvocations(), type))
            }
        } else {
            WhitelistedMethodContribution methodContribution = findAllowedMethods(type, method)
            def methodDisplay = type.getName() + "#" + method
            if (methodContribution == null) {
                throw new SecurityException("Method does not exist or is not allowed: " + methodDisplay)
            }

            def classContribution = methodContribution.getClassContribution()
            classContribution.incrementMethodInvocationCount()

            if (classContribution.getMaxMethodInvocations() > 0 && classContribution.getCurrentMethodInvocationCount() > classContribution.getMaxMethodInvocations()) {
                throw new SecurityException(String.format("The maximum method invocation count of %s for %s reached", classContribution.getMaxMethodInvocations(), type))
            }

            methodContribution.incrementInvocationCount()
            if (methodContribution.getMaxInvocationCount() > 0 && methodContribution.getCurrentInvocationCount() > methodContribution.getMaxInvocationCount()) {
                throw new SecurityException(String.format("Method '%s' may only be invoked %s times per script execution thread", methodDisplay, methodContribution.getMaxInvocationCount()))
            }
        }
    }

    WhitelistedClassContribution findAllowedClasses(Class<?> declarationClass) {
        if (declarationClass != null) {
            def allowedClassContribution = allowedClasses.get(declarationClass)
            if (allowedClassContribution != null) {
                return allowedClassContribution
            }

            for (Class<?> interfaceType : declarationClass.getInterfaces()) {
                def allowedInterface = findAllowedClasses(interfaceType)
                if (allowedInterface != null) {
                    return allowedInterface
                }
            }
            def superAllowedClassContribution = findAllowedClasses(declarationClass.getSuperclass())
            if (superAllowedClassContribution != null) {
                return superAllowedClassContribution
            }
        }

        return null
    }

    WhitelistedMethodContribution findAllowedMethods(Class<?> declaringClass, String method) {
        if (declaringClass != null) {
            Set<WhitelistedMethodContribution> methods = allowedMethods.get(declaringClass)
            if (methods != null) {
                def allowedMethodsForClass = methods.stream().filter({ contribution -> contribution.getMethod() == method }).findAny().orElse(null)
                if (allowedMethodsForClass != null) {
                    return allowedMethodsForClass
                }
            }

            for (Class<?> interfaceType : declaringClass.getInterfaces()) {
                def interfaceAllowedMethods = findAllowedMethods(interfaceType, method)
                if (interfaceAllowedMethods != null) {
                    return interfaceAllowedMethods
                }
            }
            def superClassAllowedMethods = findAllowedMethods(declaringClass.getSuperclass(), method)
            if (superClassAllowedMethods != null) {
                return superClassAllowedMethods
            }
        }

        return null
    }

    static class WhitelistedClassContribution {

        private final Class<?> type
        private final int maxMethodInvocations
        private final ThreadLocal<Integer> currentInvocationCount

        WhitelistedClassContribution(Class<?> type, int maxMethodInvocations) {
            this.type = type
            this.maxMethodInvocations = maxMethodInvocations
            currentInvocationCount = ThreadLocal.withInitial({ -> 0 })
        }

        Class<?> getType() {
            return type
        }

        int getMaxMethodInvocations() {
            return maxMethodInvocations
        }

        int getCurrentMethodInvocationCount() {
            return currentInvocationCount.get()
        }

        void incrementMethodInvocationCount() {
            currentInvocationCount.set(getCurrentMethodInvocationCount() + 1)
        }

    }

    static class WhitelistedMethodContribution {

        private final String method
        private final int maxInvocationCount
        private final ThreadLocal<Integer> currentInvocationCount
        private final WhitelistedClassContribution classContribution

        WhitelistedMethodContribution(String method, int maxInvocationCount, WhitelistedClassContribution classContribution) {
            this.method = method
            this.maxInvocationCount = maxInvocationCount
            currentInvocationCount = ThreadLocal.withInitial({ -> 0 })
            this.classContribution = classContribution
        }

        String getMethod() {
            return method
        }

        /**
         * @return the maximum amount of times this method may be invoked, 0 means there is no limit
         */
        int getMaxInvocationCount() {
            return maxInvocationCount
        }

        int getCurrentInvocationCount() {
            return currentInvocationCount.get()
        }

        void incrementInvocationCount() {
            currentInvocationCount.set(getCurrentInvocationCount() + 1)
        }

        WhitelistedClassContribution getClassContribution() {
            return classContribution
        }

    }

}

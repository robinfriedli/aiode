package net.robinfriedli.botify.scripting

import net.robinfriedli.jxp.api.XmlElement
import net.robinfriedli.jxp.persist.Context
import org.kohsuke.groovy.sandbox.GroovyInterceptor

import java.util.stream.Collectors

import static net.robinfriedli.jxp.queries.Conditions.tagName

class GroovyWhitelistInterceptor extends GroovyInterceptor {

    private final Set<Class<?>> allowedClasses
    private final Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods

    GroovyWhitelistInterceptor(Set<Class<?>> allowedClasses, Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods) {
        this.allowedClasses = allowedClasses
        this.allowedMethods = allowedMethods
    }

    static GroovyWhitelistInterceptor createFromConfiguration(Context configuration) {
        Set<Class> allowedClasses = configuration.query(tagName("whitelistAllMethods"))
            .getResultStream()
            .map({ elem -> Class.forName(elem.getAttribute("class").getValue()) })
            .collect(Collectors.toSet())

        Map<Class<?>, Set<WhitelistedMethodContribution>> allowedMethods = new HashMap<>()
        for (XmlElement methodWhitelist : configuration.query(tagName("whitelistMethods")).collect()) {
            Class<?> allowedClass = Class.forName(methodWhitelist.getAttribute("class").getValue())
            Set<WhitelistedMethodContribution> methodNames = methodWhitelist
                .query(tagName("method"))
                .getResultStream()
                .map({ elem -> new WhitelistedMethodContribution(elem.getAttribute("name").getValue(), elem.getAttribute("maxInvocationCount").getInt()) })
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
        if (!isAllowedClass(type)) {
            WhitelistedMethodContribution methodContribution = findAllowedMethods(type, method)
            def methodDisplay = type.getName() + "#" + method
            if (methodContribution == null) {
                throw new SecurityException("Method does not exist or is not allowed: " + methodDisplay)
            }
            methodContribution.incrementInvocationCount()
            if (methodContribution.getMaxInvocationCount() != 0 && methodContribution.getCurrentInvocationCount() > methodContribution.getMaxInvocationCount()) {
                throw new SecurityException(String.format("Method '%s' may only be invoked %s times per script execution thread", methodDisplay, methodContribution.getMaxInvocationCount()))
            }
        }
    }

    boolean isAllowedClass(Class<?> declarationClass) {
        if (declarationClass != null) {
            if (allowedClasses.contains(declarationClass)) {
                return true
            }

            return isAllowedClass(declarationClass.getSuperclass()) || Arrays.stream(declarationClass.getInterfaces()).anyMatch({ c -> isAllowedClass(c) })
        }

        return false
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

            def superClassAllowedMethods = findAllowedMethods(declaringClass.getSuperclass(), method)
            if (superClassAllowedMethods != null) {
                return superClassAllowedMethods
            } else {
                for (Class<?> interfaceType : declaringClass.getInterfaces()) {
                    def interfaceAllowedMethods = findAllowedMethods(interfaceType, method)
                    if (interfaceAllowedMethods != null) {
                        return interfaceAllowedMethods
                    }
                }
            }
        }

        return null
    }

    static class WhitelistedMethodContribution {

        private final String method
        private final int maxInvocationCount
        private ThreadLocal<Integer> currentInvocationCount

        WhitelistedMethodContribution(String method, int maxInvocationCount) {
            this.method = method
            this.maxInvocationCount = maxInvocationCount
            currentInvocationCount = ThreadLocal.withInitial({ -> 0 })
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

    }

}

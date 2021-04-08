package net.robinfriedli.botify.scripting;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.api.client.util.Sets;
import net.robinfriedli.botify.util.ClassDescriptorNode;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.botify.util.ClassDescriptorNode.*;
import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manages the groovy whitelist configuration offering checks for method invocations and property reassignment access
 * and invocation count management.
 */
public class GroovyWhitelistManager {

    // global counter for method invocations and loop iterations
    static final ThreadLocal<Integer> GLOBAL_INVOCATION_COUNT = ThreadLocal.withInitial(() -> 0);

    private final Map<Class<?>, WhitelistedClassContribution> whitelistContributions;

    public GroovyWhitelistManager(Map<Class<?>, WhitelistedClassContribution> whitelistContributions) {
        this.whitelistContributions = whitelistContributions;
    }

    public static GroovyWhitelistManager createFromConfiguration(Context configuration) {
        Map<Class<?>, WhitelistedClassContribution> whitelistContributions = configuration.query(tagName("whitelistClass"))
            .getResultStream()
            .map(elem -> {
                try {
                    return new WhitelistedClassContribution(
                        true,
                        elem.getAttribute("onlyGenerated").getBool() ? ClassAccessMode.GENERATED : ClassAccessMode.FULL,
                        Class.forName(elem.getAttribute("class").getValue()),
                        elem.getAttribute("maxMethodInvocations").getInt()
                    );
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Invalid whitelist configuration, invalid class encountered", e);
                }
            })
            .collect(Collectors.toMap(WhitelistedClassContribution::getType, contribution -> contribution));

        for (XmlElement methodWhitelist : configuration.query(tagName("whitelistMethods")).collect()) {
            Class<?> allowedClass;
            try {
                allowedClass = Class.forName(methodWhitelist.getAttribute("class").getValue());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Invalid whitelist configuration, invalid  class encountered", e);
            }

            WhitelistedClassContribution allowedClassContribution = new WhitelistedClassContribution(
                methodWhitelist.getAttribute("allowConstructorCall").getBool(),
                ClassAccessMode.METHODS,
                allowedClass,
                methodWhitelist.getAttribute("maxMethodInvocations").getInt()
            );

            methodWhitelist
                .query(tagName("method"))
                .getResultStream()
                .forEach(elem ->
                    new WhitelistedMethodContribution(
                        elem.getAttribute("name").getValue(),
                        !elem.hasAttribute("inheritable") || elem.getAttribute("inheritable").getBool(),
                        elem.getAttribute("onlyGenerated").getBool(),
                        elem.getAttribute("maxInvocationCount").getInt(),
                        allowedClassContribution
                    )
                );

            methodWhitelist
                .query(tagName("writeProperty"))
                .getResultStream()
                .forEach(elem ->
                    new WhitelistedPropertyWriteAccessContribution(
                        elem.getAttribute("name").getValue(),
                        !elem.hasAttribute("inheritable") || elem.getAttribute("inheritable").getBool(),
                        allowedClassContribution
                    )
                );

            whitelistContributions.put(allowedClassContribution.getType(), allowedClassContribution);
        }

        // set the parent contribution of all contributions to the contribution describing the closest superclass
        Collection<WhitelistedClassContribution> allWhitelistContributions = whitelistContributions.values();
        for (WhitelistedClassContribution whitelistContribution : allWhitelistContributions) {
            Class<?> currentType = whitelistContribution.getType();
            for (WhitelistedClassContribution otherContribution : allWhitelistContributions) {
                if (whitelistContribution == otherContribution) {
                    continue;
                }

                Class<?> otherType = otherContribution.getType();

                if (otherType.isAssignableFrom(currentType)) {
                    Collection<WhitelistedClassContribution> parentContributions = whitelistContribution.getParentContributions();

                    if (parentContributions == null || parentContributions.isEmpty()) {
                        whitelistContribution.setParentContributions(Collections.singleton(otherContribution));
                    } else {
                        Set<WhitelistedClassContribution> contendingContributions = Sets.newHashSet();
                        contendingContributions.addAll(parentContributions);
                        contendingContributions.add(otherContribution);

                        whitelistContribution.setParentContributions(selectClosestNodes(contendingContributions, currentType));
                    }
                }
            }
        }

        return new GroovyWhitelistManager(whitelistContributions);
    }

    public boolean checkMethodCall(Class<?> type, String method, boolean isGenerated) {
        Collection<WhitelistedClassContribution> whitelistContributions = findWhitelistContributions(type);
        for (WhitelistedClassContribution whitelistContribution : whitelistContributions) {
            if (("<init>".equals(method) && whitelistContribution.allowConstructorCall())
                || whitelistContribution.checkMethodAccess(type, method, isGenerated)) {
                return true;
            }
        }

        return false;
    }

    public boolean checkPropertyWriteAccess(Class<?> type, String property) {
        Collection<WhitelistedClassContribution> whitelistContributions = findWhitelistContributions(type);
        for (WhitelistedClassContribution whitelistContribution : whitelistContributions) {
            if (whitelistContribution.checkPropertyWriteAccess(type, property)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the {@link WhitelistedClassContribution} closest to the provided class (either <whitelistClass/> or <whitelistMethods/>).
     * This method automatically finds contributions for superclasses and selects the node describing the closest superclass.
     * If there are several equal contributions found (e.g.  if the type implements two interfaces and there is a contribution
     * for both) all of them are returned.
     *
     * @param type the the class for which to find a whitelist contribution
     * @return the found contributions
     */
    public Collection<WhitelistedClassContribution> findWhitelistContributions(Class<?> type) {
        Set<WhitelistedClassContribution> matchingContributions = whitelistContributions.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(type))
            .map(Map.Entry::getValue)
            .collect(Collectors.toSet());

        return selectClosestNodes(matchingContributions, type);
    }

    public void resetInvocationCounts() {
        GLOBAL_INVOCATION_COUNT.set(0);
        whitelistContributions.values().forEach(WhitelistedClassContribution::resetCurrentInvocationCount);
    }

    enum ClassAccessMode {
        // all methods
        FULL,
        // all methods, but may only be used by groovy generated code
        GENERATED,
        // access granted for certain methods
        METHODS
    }

    /**
     * Represents a class description in a whitelist contribution, either in the form of a <whitelistClass/> element, in
     * which case hasFullAccess is true, or in the form of a <whitelistMethods/> element, in which case hasFullAccess is
     * false. Instances of this class can be found using {@link #findWhitelistContributions(Class)}.
     */
    static class WhitelistedClassContribution implements ClassDescriptorNode {

        // allow calls to <init>, only relevant for <whitelistMethods/>, i.e. if hasFullAccess is false
        private final boolean allowConstructorCall;
        private final Class<?> type;
        private final ClassAccessMode accessMode;
        private final int maxMethodInvocations;
        private final ThreadLocal<Integer> currentInvocationCount;

        private final Map<String, WhitelistedMethodContribution> whitelistedMethodContributions = new HashMap<>();
        private final Map<String, WhitelistedPropertyWriteAccessContribution> whitelistedPropertyWriteAccessContributions = new HashMap<>();

        private Collection<WhitelistedClassContribution> parentContributions;

        public WhitelistedClassContribution(boolean allowConstructorCall, ClassAccessMode accessMode, Class<?> type, int maxMethodInvocations) {
            this.allowConstructorCall = allowConstructorCall;
            this.type = type;
            this.accessMode = accessMode;
            this.maxMethodInvocations = maxMethodInvocations;
            currentInvocationCount = ThreadLocal.withInitial(() -> 0);
        }

        public boolean allowConstructorCall() {
            return allowConstructorCall;
        }

        public boolean hasFullAccess(boolean isGenerated) {
            return accessMode == ClassAccessMode.FULL || (isGenerated && accessMode == ClassAccessMode.GENERATED);
        }

        /**
         * Check whether this class or a subclass thereof has been granted access to the provided method.
         *
         * @param type        the type on which the method is being accessed
         * @param method      the name of the method
         * @param isGenerated whether the method call was generated by groovy
         * @return true if access has been granted
         */
        public boolean checkMethodAccess(Class<?> type, String method, boolean isGenerated) {
            if (hasFullAccess(isGenerated)) {
                return true;
            }

            WhitelistedMethodContribution contribution = whitelistedMethodContributions.get(method);
            if (contribution != null) {
                return (contribution.isInheritable() || type.equals(this.type))
                    && (!contribution.isOnlyGenerated() || isGenerated);
            } else {
                if (parentContributions != null && !parentContributions.isEmpty()) {
                    for (WhitelistedClassContribution parentContribution : parentContributions) {
                        // only check parent contribution for access if the parent contribution does not have full access as
                        // <whitelistMethods/> for classes should override <whitelistClass/> contributions for super classes
                        if (!parentContribution.hasFullAccess(isGenerated)
                            && parentContribution.checkMethodAccess(type, method, isGenerated)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Check whether this class or a subclass thereof has been granted reassignment access to the provided property.
         *
         * @param type     the type on which the property is being accessed
         * @param property the name of the property
         * @return true if access has been granted
         */
        public boolean checkPropertyWriteAccess(Class<?> type, String property) {
            if (hasFullAccess(false)) {
                return true;
            }

            WhitelistedPropertyWriteAccessContribution contribution = whitelistedPropertyWriteAccessContributions.get(property);
            if (contribution != null) {
                return contribution.isInheritable()
                    || type.equals(this.type);
            } else {
                if (parentContributions != null && !parentContributions.isEmpty()) {
                    for (WhitelistedClassContribution parentContribution : parentContributions) {
                        // only check parent contribution for access if the parent contribution does not have full access as
                        // <whitelistMethods/> for classes should override <whitelistClass/> contributions for super classes
                        if (!parentContribution.hasFullAccess(false)
                            && parentContribution.checkPropertyWriteAccess(type, property)) {
                            return true;
                        }
                    }
                }
            }

            return false;
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
            whitelistedMethodContributions.values().forEach(WhitelistedMethodContribution::resetCurrentInvocationCount);
        }

        public void incrementMethodInvocationCount() {
            currentInvocationCount.set(getCurrentMethodInvocationCount() + 1);
        }

        public Map<String, WhitelistedMethodContribution> getWhitelistedMethodContributions() {
            return whitelistedMethodContributions;
        }

        public void addWhitelistedMethod(WhitelistedMethodContribution whitelistedMethodContribution) {
            whitelistedMethodContributions.put(whitelistedMethodContribution.getMethod(), whitelistedMethodContribution);
        }

        public Map<String, WhitelistedPropertyWriteAccessContribution> getWhitelistedPropertyWriteAccessContributions() {
            return whitelistedPropertyWriteAccessContributions;
        }

        public void addWhitelistedPropertyWriteAccessContribution(WhitelistedPropertyWriteAccessContribution contribution) {
            whitelistedPropertyWriteAccessContributions.put(contribution.getProperty(), contribution);
        }

        /**
         * @return the contributions describing the closes super classes. This only includes several items if there are
         * several contributions describing superclasses with the same inheritance level (e.g. if this type implements
         * two separate interfaces for both of which exist whitelist contributions).
         */
        public Collection<WhitelistedClassContribution> getParentContributions() {
            return parentContributions;
        }

        public void setParentContributions(Collection<WhitelistedClassContribution> parentContributions) {
            this.parentContributions = parentContributions;
        }

    }

    /**
     * Represents a whitelisted method declared by a <method/> element within a <whitelistMethods/> contribution. May be
     * retrieved from the {@link WhitelistedClassContribution} that describes the parent element.
     */
    static class WhitelistedMethodContribution implements ClassDescriptorNode {

        private final String method;
        private final boolean inheritable;
        private final boolean onlyGenerated;
        private final int maxInvocationCount;
        private final ThreadLocal<Integer> currentInvocationCount;
        private final WhitelistedClassContribution classContribution;

        public WhitelistedMethodContribution(String method, boolean inheritable, boolean onlyGenerated, int maxInvocationCount, WhitelistedClassContribution classContribution) {
            this.method = method;
            this.inheritable = inheritable;
            this.onlyGenerated = onlyGenerated;
            this.maxInvocationCount = maxInvocationCount;
            currentInvocationCount = ThreadLocal.withInitial(() -> 0);
            this.classContribution = classContribution;

            classContribution.addWhitelistedMethod(this);
        }

        public String getMethod() {
            return method;
        }

        public boolean isInheritable() {
            return inheritable;
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

        public boolean isOnlyGenerated() {
            return onlyGenerated;
        }
    }

    static class WhitelistedPropertyWriteAccessContribution implements ClassDescriptorNode {

        private final String property;
        private final boolean inheritable;
        private final WhitelistedClassContribution classContribution;

        WhitelistedPropertyWriteAccessContribution(String property, boolean inheritable, WhitelistedClassContribution classContribution) {
            this.property = property;
            this.inheritable = inheritable;
            this.classContribution = classContribution;

            classContribution.addWhitelistedPropertyWriteAccessContribution(this);
        }

        public String getProperty() {
            return property;
        }

        public boolean isInheritable() {
            return inheritable;
        }

        @Override
        public Class<?> getType() {
            return classContribution.getType();
        }

    }

}

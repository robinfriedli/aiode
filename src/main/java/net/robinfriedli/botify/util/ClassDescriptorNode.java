package net.robinfriedli.botify.util;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public interface ClassDescriptorNode {

    static <T extends ClassDescriptorNode> T selectClosestNode(Collection<T> results, Class<?> declarationClass) {
        if (results.size() == 1) {
            return results.iterator().next();
        } else if (results.isEmpty()) {
            return null;
        } else {
            Optional<T> exactMatch = results.stream().filter(contribution -> contribution.getType().equals(declarationClass)).findFirst();
            if (exactMatch.isPresent()) {
                return exactMatch.get();
            }

            // if several contributions were found describing different super classes, count the number of super (or equal)
            // classes for each, the one with the most supers is the lowest class and closest to the declaration class
            Multimap<Long, T> classesByInheritanceLevel = HashMultimap.create();
            for (T foundContribution : results) {
                Class<?> currentType = foundContribution.getType();
                long numberOfAssignables = results.stream().map(T::getType).filter(type -> type.isAssignableFrom(currentType)).count();
                classesByInheritanceLevel.put(numberOfAssignables, foundContribution);
            }

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            long maxLevelCount = classesByInheritanceLevel.entries().stream().mapToLong(Map.Entry::getKey).max().getAsLong();
            Collection<T> closestMatches = classesByInheritanceLevel.get(maxLevelCount);
            return closestMatches.iterator().next();
        }
    }

    Class<?> getType();

}

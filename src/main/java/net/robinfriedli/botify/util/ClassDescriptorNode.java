package net.robinfriedli.botify.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public interface ClassDescriptorNode {

    @Nullable
    static <T extends ClassDescriptorNode> T selectClosestNode(Collection<T> results, Class<?> declarationClass) {
        Collection<T> closestMatches = selectClosestNodes(results, declarationClass);
        Iterator<T> iterator = closestMatches.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null;
        }
    }

    static <T extends ClassDescriptorNode> Collection<T> selectClosestNodes(Collection<T> results, Class<?> declarationClass) {
        if (results.size() == 1 || results.isEmpty()) {
            return results;
        } else {
            Set<T> exactMatches = results.stream().filter(contribution -> contribution.getType().equals(declarationClass)).collect(Collectors.toSet());
            if (!exactMatches.isEmpty()) {
                return exactMatches;
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
            return classesByInheritanceLevel.get(maxLevelCount);
        }
    }

    static <T extends ClassDescriptorNode> Comparator<T> getComparator() {
        return (o1, o2) -> {
            Class<?> type1 = o1.getType();
            Class<?> type2 = o2.getType();

            if (type2.isAssignableFrom(type1)) {
                return -1;
            } else if (type1.isAssignableFrom(type2)) {
                return 1;
            }

            return 0;
        };
    }

    Class<?> getType();

}

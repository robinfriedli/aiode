package net.robinfriedli.botify.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.robinfriedli.botify.discord.GuildContext;
import org.jetbrains.annotations.NotNull;

/**
 * An AbstractMap implementation that maps a discord snowflake (e.g user) to any value of type V and uses the
 * snowflake's id instead of the {@link Object#equals(Object)} method. This map is mainly used for caching of, for
 * example, {@link GuildContext} and thus can experience many concurrent operations under high traffic.
 */
public class ISnowflakeMap<V> extends AbstractMap<ISnowflake, V> {

    private final ISnowflakeEntrySet<V> entrySet = new ISnowflakeEntrySet<>();

    @Override
    public V get(Object key) {
        if (key instanceof ISnowflake) {
            return entrySet.get((ISnowflake) key);
        }

        return null;
    }

    @Override
    public V put(ISnowflake iSnowflake, V value) {
        if (iSnowflake == null) {
            throw new UnsupportedOperationException("Null keys not supported");
        }

        return entrySet.addOrUpdateEntry(iSnowflake, value);
    }

    @Override
    public V remove(Object key) {
        if (key instanceof ISnowflake) {
            return entrySet.removeEntryFor((ISnowflake) key);
        }

        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof ISnowflake) {
            return entrySet.containsEntryFor((ISnowflake) key);
        }

        return false;
    }

    @NotNull
    @Override
    public Set<Entry<ISnowflake, V>> entrySet() {
        return entrySet;
    }

    private static class ISnowflakeEntrySet<V> extends AbstractSet<Entry<ISnowflake, V>> {

        private final Map<Long, Map.Entry<ISnowflake, V>> mappedValues = new HashMap<>();

        @NotNull
        @Override
        public Iterator<Entry<ISnowflake, V>> iterator() {
            return mappedValues.values().iterator();
        }

        @Override
        public int size() {
            return mappedValues.size();
        }

        @Override
        public boolean add(Map.Entry<ISnowflake, V> entry) {
            long id = entry.getKey().getIdLong();
            return mappedValues.put(id, entry) == null;
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Map.Entry) {
                Object key = ((Map.Entry) o).getKey();
                if (key instanceof ISnowflake) {
                    return mappedValues.containsKey(((ISnowflake) key).getIdLong());
                }
            }

            return false;
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Object key = ((Map.Entry) o).getKey();
                if (key instanceof ISnowflake) {
                    Map.Entry<ISnowflake, V> removedEntry = mappedValues.remove(((ISnowflake) key).getId());
                    return removedEntry != null;
                }
            }

            return false;
        }

        /**
         * @return the value of the entry mapped to the id of the provided {@link ISnowflake} directly.
         */
        private V get(ISnowflake iSnowflake) {
            Entry<ISnowflake, V> iSnowflakeEntry = mappedValues.get(iSnowflake.getIdLong());
            return iSnowflakeEntry != null ? iSnowflakeEntry.getValue() : null;
        }

        private boolean containsEntryFor(ISnowflake iSnowflake) {
            return mappedValues.containsKey(iSnowflake.getIdLong());
        }

        /**
         * Add or update the existing entry for the provided snowflake directly without having to iterate over the entry
         * map of the surrounding ISnowflakeMap to find the entry by querying the mapped entry directly.
         *
         * @param iSnowflake the snowflake to add a new entry for or update
         * @param value      the new value to assign to the new entry or update the existing one with
         * @return the previous value or null
         */
        private V addOrUpdateEntry(ISnowflake iSnowflake, V value) {
            long id = iSnowflake.getIdLong();
            Entry<ISnowflake, V> existingEntry = mappedValues.get(id);
            V oldVal = null;
            if (existingEntry != null) {
                oldVal = existingEntry.getValue();
                existingEntry.setValue(value);
            } else {
                mappedValues.put(id, new SimpleEntry<>(iSnowflake, value));
            }

            return oldVal;
        }

        /**
         * Remove the entry mapped to the id of this snowflake directly instead of first having to search the entry
         *
         * @param iSnowflake the {@link ISnowflake} to remove
         * @return the value of entry that was removed or null
         */
        private V removeEntryFor(ISnowflake iSnowflake) {
            Entry<ISnowflake, V> removedEntry = mappedValues.remove(iSnowflake.getIdLong());
            return removedEntry != null ? removedEntry.getValue() : null;
        }

    }

}

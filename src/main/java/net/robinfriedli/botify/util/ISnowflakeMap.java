package net.robinfriedli.botify.util;

import java.util.AbstractMap;
import java.util.Set;

import javax.annotation.Nullable;

import net.dv8tion.jda.core.entities.ISnowflake;
import org.jetbrains.annotations.NotNull;

/**
 * A simple AbstractMap implementation that maps a discord snowflake (e.g user) to any value of type V and uses the
 * snowflake's id instead of the {@link Object#equals(Object)} method
 */
public class ISnowflakeMap<V> extends AbstractMap<ISnowflake, V> {

    private final Set<Entry<ISnowflake, V>> entrySet = new ISnowflakeEntrySet<>();

    @Override
    public V get(Object key) {
        if (key instanceof ISnowflake) {
            Entry<ISnowflake, V> entry = getEntry((ISnowflake) key);
            if (entry != null) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public V put(ISnowflake iSnowflake, V value) {
        if (iSnowflake == null) {
            throw new UnsupportedOperationException("Null keys not supported");
        }

        Entry<ISnowflake, V> entry = getEntry(iSnowflake);
        if (entry != null) {
            V oldValue = entry.getValue();
            entry.setValue(value);

            return oldValue;
        } else {
            entrySet.add(new SimpleEntry<>(iSnowflake, value));
        }

        return null;
    }

    @Override
    public V remove(Object key) {
        if (key instanceof ISnowflake) {
            Entry<ISnowflake, V> entry = getEntry((ISnowflake) key);
            if (entry != null) {
                entrySet.remove(entry);
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof ISnowflake) {
            return getEntry((ISnowflake) key) != null;
        }

        return false;
    }

    @NotNull
    @Override
    public Set<Entry<ISnowflake, V>> entrySet() {
        return entrySet;
    }

    @Nullable
    private Entry<ISnowflake, V> getEntry(ISnowflake iSnowflake) {
        for (Entry<ISnowflake, V> entry : entrySet) {
            if (entry.getKey().getId().equals(iSnowflake.getId())) {
                return entry;
            }
        }

        return null;
    }

}

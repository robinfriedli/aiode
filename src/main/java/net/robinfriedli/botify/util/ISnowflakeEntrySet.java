package net.robinfriedli.botify.util;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.dv8tion.jda.core.entities.ISnowflake;

public class ISnowflakeEntrySet<V> extends AbstractSet<Map.Entry<ISnowflake, V>> {

    private final Map<String, Map.Entry<ISnowflake, V>> mappedValues = new HashMap<>();

    @Override
    public Iterator<Map.Entry<ISnowflake, V>> iterator() {
        return mappedValues.values().iterator();
    }

    @Override
    public int size() {
        return mappedValues.size();
    }

    @Override
    public boolean add(Map.Entry<ISnowflake, V> entry) {
        String id = entry.getKey().getId();
        if (!mappedValues.containsKey(id)) {
            mappedValues.put(id, entry);
            return true;
        }

        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Map.Entry) {
            Object key = ((Map.Entry) o).getKey();
            if (key instanceof ISnowflake) {
                return mappedValues.containsKey(((ISnowflake) key).getId());
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

}

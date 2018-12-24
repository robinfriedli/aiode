package net.robinfriedli.botify.util;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.dv8tion.jda.core.entities.User;
import org.jetbrains.annotations.NotNull;

/**
 * A simple AbstractMap implementation that maps a user to any value of type V and uses the user's id instead of the
 * {@link Object#equals(Object)} method
 */
public class UserMap<V> extends AbstractMap<User, V> {

    private final Set<Entry<User, V>> entrySet = new HashSet<>();

    @Override
    public V get(Object key) {
        if (key instanceof User) {
            Entry<User, V> entry = getEntry((User) key);
            if (entry != null) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public V put(User user, V value) {
        if (user == null) {
            throw new UnsupportedOperationException("Null keys not supported");
        }

        Entry<User, V> entry = getEntry(user);
        if (entry != null) {
            V oldValue = entry.getValue();
            entry.setValue(value);

            return oldValue;
        } else {
            entrySet.add(new SimpleEntry<>(user, value));
        }

        return null;
    }

    @Override
    public V remove(Object key) {
        if (key instanceof User) {
            Entry<User, V> entry = getEntry((User) key);
            if (entry != null) {
                entrySet.remove(entry);
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof User) {
            return getEntry((User) key) != null;
        }

        return false;
    }

    @NotNull
    @Override
    public Set<Entry<User, V>> entrySet() {
        return entrySet;
    }

    @Nullable
    private Entry<User, V> getEntry(User user) {
        for (Entry<User, V> entry : entrySet) {
            if (entry.getKey().getId().equals(user.getId())) {
                return entry;
            }
        }

        return null;
    }

}

package net.robinfriedli.botify.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;

/**
 * Utility class that aids loading a large number of items in as little requests as possible and performs an action with
 * each result
 *
 * @param <K> the type of key to load each result with
 * @param <V> the type of items that are operated upon after loading
 */
public class BulkOperationService<K, V> {

    // the amount of items that can be loaded within one request
    private final int size;
    // the function that executes loading the items, the provided list does not exceed the size defined by the size
    // parameter; if the amount of items loaded exceeds the size this function is called several times; returns the loaded
    // items paired with the key they were loaded with
    private final Function<List<K>, List<Pair<K, V>>> loadFunc;
    // the keys for all items that will be loaded
    private final List<K> keys = Lists.newArrayList();
    // the map containing all keys mapped to the action that should be performed with the loaded item
    private final Map<K, ResultConsumerManager<V>> actionMap = new HashMap<>();

    public BulkOperationService(int size, Function<List<K>, List<Pair<K, V>>> loadFunc) {
        this.size = size;
        this.loadFunc = loadFunc;
    }

    public void perform() {
        List<List<K>> batches = Lists.partition(keys, size);
        for (List<K> batch : batches) {
            List<Pair<K, V>> loadedBatch = loadFunc.apply(batch);
            for (Pair<K, V> keyValuePair : loadedBatch) {
                K key = keyValuePair.getLeft();
                V value = keyValuePair.getRight();
                actionMap.get(key).next().accept(value);
            }
        }
    }

    /**
     * Add a key to load an item with and define an action to perform with the loaded item
     *
     * @param key    the key to load the item with
     * @param action the action to run with the loaded item
     */
    public void add(K key, Consumer<V> action) {
        if (key == null || action == null) {
            throw new NullPointerException();
        }

        keys.add(key);
        ResultConsumerManager<V> existingManager = actionMap.get(key);
        if (existingManager != null) {
            existingManager.add(action);
        } else {
            ResultConsumerManager<V> resultConsumerManager = new ResultConsumerManager<>();
            resultConsumerManager.add(action);
            actionMap.put(key, resultConsumerManager);
        }
    }

    private static class ResultConsumerManager<T> {

        private final List<Consumer<T>> resultConsumers = Lists.newArrayList();
        private Iterator<Consumer<T>> iterator = null;

        Consumer<T> next() {
            if (iterator == null) {
                iterator = resultConsumers.iterator();
            }

            if (!iterator.hasNext()) {
                throw new IllegalStateException("No next result consumer. " +
                    "Number of provided result consumers must be at least the number of times the key yields a result, " +
                    "i.e. the key was submitted.");
            }

            return iterator.next();
        }

        void add(Consumer<T> consumer) {
            if (iterator != null) {
                // invalidate iterator
                iterator = null;
            }
            resultConsumers.add(consumer);
        }

    }

}

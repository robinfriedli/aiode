package net.robinfriedli.botify.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

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
    // items mapped to the key they were loaded with
    private final Function<List<K>, Map<V, K>> loadFunc;
    // the keys for all items that will be loaded
    private final List<K> keys = Lists.newArrayList();
    // the map containing all keys mapped to the action that should be performed with the loaded item
    private final Map<K, Consumer<V>> actionMap = new HashMap<>();

    public BulkOperationService(int size, Function<List<K>, Map<V, K>> loadFunc) {
        this.size = size;
        this.loadFunc = loadFunc;
    }

    public void perform() {
        List<List<K>> batches = Lists.partition(keys, size);
        for (List<K> batch : batches) {
            Map<V, K> loadedBatch = loadFunc.apply(batch);
            for (V v : loadedBatch.keySet()) {
                K k = loadedBatch.get(v);
                actionMap.get(k).accept(v);
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
        keys.add(key);
        actionMap.put(key, action);
    }

}

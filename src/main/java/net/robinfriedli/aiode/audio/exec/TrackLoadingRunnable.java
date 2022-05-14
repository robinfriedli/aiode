package net.robinfriedli.aiode.audio.exec;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.robinfriedli.aiode.function.ChainableRunnable;

/**
 * Interface for tasks that loads data for tracks asynchronously, e.g. populating YouTube playlists or fetching the matching
 * YouTube video for a Spotify track.
 *
 * @param <T> type of the objects to handle
 */
public interface TrackLoadingRunnable<T> extends ChainableRunnable {

    default void addItem(T item) {
        addItems(Collections.singleton(item));
    }

    void addItems(Collection<T> items);

    List<T> getItems();

    void handleCancellation();

    void loadItem(T item) throws Exception;

    default void loadItems() throws Exception {
        for (T item : getItems()) {
            if (Thread.interrupted()) {
                handleCancellation();
                break;
            }

            try {
                loadItem(item);
            } catch (Exception e) {
                handleCancellation();
                throw e;
            }
        }
    }

    @Override
    default void doRun() throws Exception {
        loadItems();
    }
}

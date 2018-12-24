package net.robinfriedli.botify.audio;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

public class AudioQueue {

    private final List<Playable> currentQueue = Lists.newArrayList();
    private int currentTrack = 0;

    public List<Playable> getTracks() {
        return currentQueue;
    }

    public int getPosition() {
        return currentTrack;
    }

    public Playable getCurrent() {
        return currentQueue.get(currentTrack);
    }

    public Playable getNext() {
        next();
        return getCurrent();
    }

    public void next() {
        ++currentTrack;
    }

    public Playable getPrevious() {
        previous();
        return getCurrent();
    }

    public void previous() {
        --currentTrack;
    }

    public void add(Playable... tracks) {
        add(Arrays.asList(tracks));
    }

    public void add(List<Playable> tracks) {
        currentQueue.addAll(tracks);
    }

    public void set(Playable... tracks) {
        set(Arrays.asList(tracks));
    }

    public void set(List<Playable> tracks) {
        currentQueue.clear();
        reset();
        currentQueue.addAll(tracks);
    }

    public void clear() {
        currentQueue.clear();
        reset();
    }

    public boolean hasNext() {
        return currentTrack < currentQueue.size() - 1;
    }

    public boolean hasPrevious() {
        return currentTrack > 0;
    }

    public boolean isEmpty() {
        return currentQueue.isEmpty();
    }

    public void reset() {
        currentTrack = 0;
    }

}

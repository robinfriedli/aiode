package net.robinfriedli.botify.audio;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.Util;

public class AudioQueue {

    private final List<Playable> currentQueue = Lists.newArrayList();
    private int currentTrack = 0;
    private boolean isShuffle = false;
    private List<Integer> randomizedOrder = Lists.newArrayList();

    public List<Playable> getTracks() {
        return currentQueue;
    }

    public int getPosition() {
        return currentTrack;
    }

    public void setPosition(int position) {
        currentTrack = position;
    }

    public int getCurrentTrackNumber() {
        if (isShuffle) {
            return randomizedOrder.get(currentTrack);
        } else {
            return currentTrack;
        }
    }

    public Playable getCurrent() {
        if (isShuffle) {
            return currentQueue.get(randomizedOrder.get(currentTrack));
        } else {
            return currentQueue.get(currentTrack);
        }
    }

    public Playable getNext() {
        next();
        return getCurrent();
    }

    public EmbedBuilder buildMessageEmbed(AudioPlayback playback, Guild guild) {
        Playable current = getCurrent();
        int position = getPosition();
        List<Playable> tracks = getTracks();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");

        embedBuilder.addField("Paused", boolToString(playback.isPaused()), true);
        embedBuilder.addField("Shuffle", boolToString(playback.isShuffle()), true);
        embedBuilder.addField("Repeat all", boolToString(playback.isRepeatAll()), true);
        embedBuilder.addField("Repeat one", boolToString(playback.isRepeatOne()), true);
        String url = baseUri + String.format("/queue?guildId=%s", guild.getId());
        embedBuilder.addField("", "[Full list](" + url + ")", false);

        StringBuilder prevBuilder = new StringBuilder();
        StringBuilder nextBuilder = new StringBuilder();

        if (position > 0) {
            List<Playable> previous;
            if (position > 5) {
                prevBuilder.append("...").append(System.lineSeparator());
            }
            previous = listPrevious(5);
            for (Playable prev : previous) {
                appendPlayable(prevBuilder, prev);
            }
        }

        if (!prevBuilder.toString().isEmpty()) {
            embedBuilder.addField("Previous", prevBuilder.toString(), false);
        }

        String currentPosition = Util.normalizeMillis(playback.getCurrentPositionMs());
        String duration = Util.normalizeMillis(current.getDurationMsInterruptible());
        embedBuilder.addField(
            "Current",
            "| " + current.getDisplayInterruptible() + " - " + currentPosition + " / " + duration,
            false
        );

        if (position < tracks.size() - 1) {
            List<Playable> next;
            next = listNext(5);
            for (Playable n : next) {
                appendPlayable(nextBuilder, n);
            }
            if (tracks.size() > position + 6) {
                nextBuilder.append("...");
            }
        }

        if (!nextBuilder.toString().isEmpty()) {
            embedBuilder.addField("Next", nextBuilder.toString(), false);
        }

        embedBuilder.setColor(Color.decode("#1DB954"));
        return embedBuilder;
    }

    public List<Playable> listNext(int max) {
        List<Playable> next = Lists.newArrayList();
        int count = 0;
        for (int i = currentTrack + 1; i < currentQueue.size() && count < max; i++) {
            if (isShuffle) {
                next.add(currentQueue.get(randomizedOrder.get(i)));
            } else {
                next.add(currentQueue.get(i));
            }
            ++count;
        }

        return next;
    }

    public void next() {
        ++currentTrack;
    }

    public Playable getPrevious() {
        previous();
        return getCurrent();
    }

    public List<Playable> listPrevious(int max) {
        List<Playable> previous = Lists.newArrayList();
        int count = 0;
        for (int i = currentTrack - 1; i >= 0 && count < max; i--) {
            if (isShuffle) {
                previous.add(currentQueue.get(randomizedOrder.get(i)));
            } else {
                previous.add(currentQueue.get(i));
            }
            ++count;
        }

        Collections.reverse(previous);
        return previous;
    }

    public void previous() {
        --currentTrack;
    }

    public void add(Playable... tracks) {
        add(Arrays.asList(tracks));
    }

    public void add(List<Playable> tracks) {
        if (isShuffle()) {
            appendRandomized(tracks);
        }
        currentQueue.addAll(tracks);
    }

    public void set(Playable... tracks) {
        set(Arrays.asList(tracks));
    }

    public void set(List<Playable> tracks) {
        clear();
        currentQueue.addAll(tracks);
        if (isShuffle()) {
            List<Integer> indices = IntStream.range(0, currentQueue.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(indices);
            randomizedOrder.addAll(indices);
        }
    }

    public void clear() {
        clear(false);
    }

    public void clear(boolean retainCurrent) {
        if (!isEmpty() && retainCurrent) {
            currentQueue.retainAll(Collections.singleton(getCurrent()));
            reset();
            randomize();
        } else {
            currentQueue.clear();
            randomizedOrder.clear();
            reset();
        }
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

    public void setShuffle(boolean isShuffle) {
        if (!isEmpty()) {
            if (isShuffle) {
                randomize();
            } else if (this.isShuffle) {
                // when setting the queue from shuffle back to normal the current track index has to be adjusted since
                // currentTrack acts as cursor rather than actual queue position in shuffle mode
                currentTrack = randomizedOrder.get(currentTrack);
            }
        }
        this.isShuffle = isShuffle;
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public void randomize() {
        randomizedOrder.clear();
        if (currentTrack > 0) {
            List<Integer> indices = IntStream.range(0, currentTrack).boxed().collect(Collectors.toList());
            Collections.shuffle(indices);
            randomizedOrder.addAll(indices);
        }
        randomizedOrder.add(currentTrack);
        if (currentTrack < currentQueue.size() - 1) {
            List<Integer> indices = IntStream.range(currentTrack + 1, currentQueue.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(indices);
            randomizedOrder.addAll(indices);
        }
    }

    private void appendRandomized(List<Playable> playables) {
        int current = currentQueue.size();
        List<Integer> indices = IntStream.range(current, current + playables.size()).boxed().collect(Collectors.toList());
        Collections.shuffle(indices);
        randomizedOrder.addAll(indices);
    }

    private String boolToString(boolean bool) {
        return bool ? "On" : "Off";
    }

    private void appendPlayable(StringBuilder trackListBuilder, Playable playable) {
        String display = playable.getDisplayInterruptible();
        long durationMs = playable.getDurationMsInterruptible();
        trackListBuilder.append("| ").append(display).append(" - ").append(Util.normalizeMillis(durationMs)).append(System.lineSeparator());
    }

}

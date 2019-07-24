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
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.discord.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.EmojiConstants;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.botify.util.Util;

public class AudioQueue {

    private final List<Playable> currentQueue = Lists.newArrayList();
    private int currentTrack = 0;
    private boolean isShuffle = false;
    private List<Integer> randomizedOrder = Lists.newArrayList();
    private boolean repeatOne;
    private boolean repeatAll;

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
        if (isEmpty()) {
            throw new NoResultsFoundException("Queue is empty");
        }

        if (isShuffle()) {
            return currentQueue.get(randomizedOrder.get(currentTrack));
        } else {
            return currentQueue.get(currentTrack);
        }
    }

    /**
     * iterate the queue to the next item
     */
    public void iterate() {
        if (!hasNext()) {
            throw new NoResultsFoundException("No next item in queue");
        }

        currentTrack = nextPosition();
    }

    /**
     * reverse the queue to the previous item
     */
    public void reverse() {
        if (!hasPrevious()) {
            throw new NoResultsFoundException("No previous item in queue");
        }

        currentTrack = previousPosition();
    }

    /**
     * @return the next item to play in the queue without iterating the queue
     */
    public Playable getNext() {
        if (!hasNext()) {
            return null;
        }

        if (isRepeatOne()) {
            return getCurrent();
        }

        if (isShuffle()) {
            return currentQueue.get(randomizedOrder.get(nextPosition()));
        } else {
            return currentQueue.get(nextPosition());
        }
    }

    public EmbedBuilder buildMessageEmbed(AudioPlayback playback, Guild guild) {
        int position = getPosition();
        List<Playable> tracks = getTracks();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");

        StringBuilder optionBuilder = new StringBuilder();
        appendIcon(optionBuilder, EmojiConstants.PLAY, playback.isPlaying());
        appendIcon(optionBuilder, EmojiConstants.PAUSE, playback.isPaused());
        appendIcon(optionBuilder, EmojiConstants.SHUFFLE, playback.isShuffle());
        appendIcon(optionBuilder, EmojiConstants.REPEAT, playback.isRepeatAll());
        appendIcon(optionBuilder, EmojiConstants.REPEAT_ONE, playback.isRepeatOne());
        optionBuilder.append(EmojiConstants.VOLUME).append(playback.getVolume());
        embedBuilder.setDescription(optionBuilder.toString());

        String url = baseUri + String.format("/queue?guildId=%s", guild.getId());
        embedBuilder.addField("", "[Full list](" + url + ")", false);

        if (isEmpty()) {
            embedBuilder.addField("", "(emtpy)", false);
        } else {
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
            Playable current = getCurrent();
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
        }

        Color color = StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification specification = Botify.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            return ColorSchemeProperty.getColor(specification);
        });
        embedBuilder.setColor(color);
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
        return hasNext(false);
    }

    public boolean hasNext(boolean ignoreRepeat) {
        if (isEmpty()) {
            return false;
        }
        boolean inBound = currentTrack < currentQueue.size() - 1;
        return ignoreRepeat ? inBound : inBound || isRepeatOne() || isRepeatAll();
    }

    public boolean hasPrevious() {
        return hasPrevious(false);
    }

    public boolean hasPrevious(boolean ignoreRepeat) {
        if (isEmpty()) {
            return false;
        }
        boolean inBound = currentTrack > 0;
        return ignoreRepeat ? inBound : inBound || isRepeatOne() || isRepeatAll();
    }

    public boolean isEmpty() {
        return currentQueue.isEmpty();
    }

    public void reset() {
        currentTrack = 0;
    }

    public boolean isShuffle() {
        return isShuffle;
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

    public void randomize() {
        randomize(true);
    }

    public void randomize(boolean protectCurrent) {
        randomizedOrder.clear();
        if (protectCurrent) {
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
        } else {
            List<Integer> indices = IntStream.range(0, currentQueue.size()).boxed().collect(Collectors.toList());
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

    private int nextPosition() {
        if (currentTrack < currentQueue.size() - 1) {
            return currentTrack + 1;
        } else {
            return 0;
        }
    }

    private int previousPosition() {
        if (currentTrack > 0) {
            return currentTrack - 1;
        } else {
            return isRepeatAll() ? getTracks().size() - 1 : 0;
        }
    }

    private void appendIcon(StringBuilder builder, String unicode, boolean enabled) {
        if (enabled) {
            builder.append(unicode);
        }
    }

    private void appendPlayable(StringBuilder trackListBuilder, Playable playable) {
        String display = playable.getDisplayInterruptible();
        long durationMs = playable.getDurationMsInterruptible();
        trackListBuilder.append("| ").append(display).append(" - ").append(Util.normalizeMillis(durationMs)).append(System.lineSeparator());
    }

    public boolean isRepeatOne() {
        return repeatOne;
    }

    public void setRepeatOne(boolean repeatOne) {
        this.repeatOne = repeatOne;
    }

    public boolean isRepeatAll() {
        return repeatAll;
    }

    public void setRepeatAll(boolean repeatAll) {
        this.repeatAll = repeatAll;
    }
}

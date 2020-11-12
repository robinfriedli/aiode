package net.robinfriedli.botify.audio;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.botify.util.EmojiConstants;
import net.robinfriedli.botify.util.Util;

/**
 * Holds and manages all tracks that are currently in the guild's queue. There is always exactly one AudioQueue on the
 * guilds {@link AudioPlayback}
 */
public class AudioQueue {

    private final List<Playable> currentQueue = Lists.newArrayList();
    private final List<Integer> randomizedOrder = Lists.newArrayList();
    @Nullable
    private final Integer maxSize;
    private int currentTrack = 0;
    private boolean isShuffle = false;
    private boolean repeatOne;
    private boolean repeatAll;

    public AudioQueue(@Nullable Integer maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * @return all playables that are currently in this queue
     */
    public List<Playable> getTracks() {
        return currentQueue;
    }

    /**
     * @return the current cursor position of the queue that is incremented by one after each track irregardless of
     * the shuffle option
     */
    public int getPosition() {
        return currentTrack;
    }

    public void setPosition(int position) {
        currentTrack = position;
    }

    /**
     * @return the index of the current track in the playlist list in its normal order, this is differs from the
     * {@link #getPosition()} method only when shuffle is enabled
     */
    public int getCurrentTrackNumber() {
        if (isShuffle) {
            return randomizedOrder.get(currentTrack);
        } else {
            return currentTrack;
        }
    }

    /**
     * @return the {@link Playable} referenced by this queue's current position
     */
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

    /**
     * Format the current queue as a Discord embed message showing all enabled options, such as shuffle and repeat,
     * the volume and previous 5, the current and the next 5 tracks and also provides a link to view the full queue
     *
     * @return the {@link EmbedBuilder} to build send to Discord with the colour specified by the {@link GuildSpecification}
     * already applied
     */
    public EmbedBuilder buildMessageEmbed(AudioPlayback playback, Guild guild) {
        int position = getPosition();
        List<Playable> tracks = getTracks();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        SpringPropertiesConfig springPropertiesConfig = Botify.get().getSpringPropertiesConfig();
        String baseUri = springPropertiesConfig.requireApplicationProperty("botify.server.base_uri");

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
                if (position > 5) {
                    prevBuilder.append("...").append(System.lineSeparator());
                }
                List<Playable> previous = listPrevious(5);
                for (Playable prev : previous) {
                    appendPlayable(prevBuilder, prev);
                }
            }

            if (!prevBuilder.toString().isEmpty()) {
                embedBuilder.addField("Previous", prevBuilder.toString(), false);
            }

            String currentPosition = Util.normalizeMillis(playback.getCurrentPositionMs());
            Playable current = getCurrent();
            String duration = Util.normalizeMillis(current.durationMs());
            embedBuilder.addField(
                "Current",
                "| " + current.getDisplayNow() + " - " + currentPosition + " / " + duration,
                false
            );

            if (position < tracks.size() - 1) {
                List<Playable> next = listNext(5);
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

            String albumCoverUrl = current.getAlbumCoverUrl();
            embedBuilder.setThumbnail(Objects.requireNonNullElseGet(albumCoverUrl, () -> baseUri + "/resources-public/img/botify-logo.png"));
        }

        Color color = StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification specification = Botify.get().getGuildManager().getContextForGuild(guild).getSpecification(session);
            return ColorSchemeProperty.getColor(specification);
        });
        embedBuilder.setColor(color);
        return embedBuilder;
    }

    /**
     * @param max the maximum amount of tracks to return
     * @return a list of tracks that follow the current track, considering the shuffle option, in the order they will be
     * played
     */
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

    /**
     * @param max the maximum amount of tracks to return
     * @return a list of tracks that precede the current track, considering the shuffle option, in the order they were
     * played
     */
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

    /**
     * add tracks to this queue, shuffling the newly added tracks if shuffle is enabled
     */
    public void add(List<Playable> tracks) {
        checkSize(tracks.size());
        if (isShuffle()) {
            appendRandomized(tracks);
        }
        currentQueue.addAll(tracks);
    }

    public void set(Playable... tracks) {
        set(Arrays.asList(tracks));
    }

    /**
     * clear the current queue and add new tracks, shuffling the newly added tracks if shuffle is enabled
     */
    public void set(List<Playable> tracks) {
        clear();
        checkSize(tracks.size());
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

    /**
     * Clear the current tracks in this queue
     *
     * @param retainCurrent keeps the track that is referenced by the currentTrack index in the queue, this is used
     *                      when the track is currently being played
     */
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

    /**
     * @param ignoreRepeat whether to ignore the repeatAll and repeatOne options
     * @return if ignoreRepeat is false this returns true if another track will or can be played after the current
     * track finishes, meaning if either repeat option is enabled this always returns true;
     * if ignoreRepeat this returns true only when the current track is not the last track in the current list of tracks
     */
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

    /**
     * @param ignoreRepeat whether to ignore the repeatAll and repeatOne options
     * @return if ignoreRepeat is false this returns true if another track will or can be played when using the rewind
     * command, meaning if either repeat option is enabled this always returns true;
     * if ignoreRepeat this returns true only when the current track is not the first track in the current list of tracks
     */
    public boolean hasPrevious(boolean ignoreRepeat) {
        if (isEmpty()) {
            return false;
        }
        boolean inBound = currentTrack > 0;
        return ignoreRepeat ? inBound : inBound || isRepeatOne() || isRepeatAll();
    }

    /**
     * @return if the list of current tracks is empty
     */
    public boolean isEmpty() {
        return currentQueue.isEmpty();
    }

    /**
     * sets the position of the queue back to 0, typically used when clearing the queue or when the playback finishes
     */
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

    /**
     * Generates the random queue order when enabling the shuffle option
     *
     * @param protectCurrent if true this makes sure that the current track will remain in the same position, used
     *                       when the playback is currently playing
     */
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
            builder.append(unicode).append(" ");
        }
    }

    private void appendPlayable(StringBuilder trackListBuilder, Playable playable) {
        playable.fetch();
        String display = playable.display(2, TimeUnit.SECONDS);
        long durationMs = playable.durationMs(2, TimeUnit.SECONDS);

        if (display.length() > 100) {
            display = display.substring(0, 95) + "[...]";
        }

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

    private void checkSize(int toAddSize) {
        if (maxSize != null) {
            if (toAddSize + getTracks().size() > maxSize) {
                throw new InvalidCommandException("Queue exceeds maximum size of " + maxSize + " tracks");
            }
        }
    }

}

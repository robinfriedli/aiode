package net.robinfriedli.aiode.audio;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Loads an {@link AudioTrack} or {@link AudioPlaylist} from lavaplayer from any given URL which then can be played by
 * lavaplayer's {@link AudioPlayer}
 */
public class AudioTrackLoader {

    private final AudioPlayerManager playerManager;
    private final Logger logger;

    public AudioTrackLoader(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Load an {@link AudioTrack} or {@link AudioPlaylist} via an identifier. This is usually its URL or a YouTube
     * search query, if preceded by the prefix "ytsearch:"
     *
     * @param identifier the url or YouTube search query
     * @return the loaded {@link AudioTrack} or {@link AudioPlaylist} or the resulting {@link FriendlyException} or null
     */
    @Nullable
    public AudioItem loadByIdentifier(String identifier) {
        return loadByIdentifier(identifier, 2, TimeUnit.MINUTES);
    }

    /**
     * Load an {@link AudioTrack} or {@link AudioPlaylist} via an identifier. This is usually its URL or a YouTube
     * search query, if preceded by the prefix "ytsearch:"
     *
     * @param identifier the url or YouTube search query
     * @param timeout    amount of time to wait for result
     * @param unit       unit of time
     * @return the loaded {@link AudioTrack} or {@link AudioPlaylist} or the resulting {@link FriendlyException} or null
     */
    @Nullable
    public AudioItem loadByIdentifier(String identifier, long timeout, TimeUnit unit) {
        CompletableFuture<AudioItem> result = new CompletableFuture<>();
        playerManager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                result.complete(audioTrack);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                result.complete(audioPlaylist);
            }

            @Override
            public void noMatches() {
                result.cancel(false);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                result.completeExceptionally(e);
                if (e.severity != FriendlyException.Severity.COMMON) {
                    logger.error("lavaplayer threw an exception while loading track " + identifier, e);
                }
            }
        });

        try {
            return result.get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause != null) {
                throw new RuntimeException(cause);
            }

            throw new RuntimeException(e);
        } catch (TimeoutException | CancellationException e) {
            return null;
        }
    }

}

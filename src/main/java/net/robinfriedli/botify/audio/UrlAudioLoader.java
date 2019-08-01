package net.robinfriedli.botify.audio;

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
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Loads an {@link AudioTrack} or {@link AudioPlaylist} from lavaplayer from any given URL which then can be played by
 * lavaplayer's {@link AudioPlayer}
 */
class UrlAudioLoader {

    private final AudioPlayerManager playerManager;
    private final Logger logger;

    UrlAudioLoader(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Nullable
    Object loadUrl(String playbackUrl) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        playerManager.loadItem(playbackUrl, new AudioLoadResultHandler() {
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
                result.complete(e);
                if (e.severity != FriendlyException.Severity.COMMON) {
                    logger.error("lavaplayer threw an exception while loading track " + playbackUrl, e);
                }
            }
        });

        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException | CancellationException e) {
            return null;
        }
    }

}

package net.robinfriedli.botify.audio.spotify;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.entities.SpotifyRedirectIndex;
import net.robinfriedli.botify.entities.SpotifyRedirectIndexModificationLock;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;

import static net.robinfriedli.botify.entities.SpotifyRedirectIndex.*;

/**
 * Service that aids loading the corresponding YouTube video for a Spotify track since Spotify does not allow playback
 * of full tracks via its api. Checks if there is a persisted {@link SpotifyRedirectIndex} or loads the YouTube video
 * via {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)} if not.
 */
public class SpotifyRedirectService {

    private static final ExecutorService SINGE_THREAD_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(new LoggingThreadFactory("spotify-redirect-service-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(SINGE_THREAD_EXECUTOR_SERVICE));
    }

    private final HibernateInvoker invoker;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Session session;
    private final YouTubeService youTubeService;

    public SpotifyRedirectService(Session session, YouTubeService youTubeService) {
        invoker = HibernateInvoker.create();
        this.session = session;
        this.youTubeService = youTubeService;
    }

    public void redirectTrack(HollowYouTubeVideo youTubeVideo) throws IOException {
        Track spotifyTrack = youTubeVideo.getRedirectedSpotifyTrack();

        if (spotifyTrack == null) {
            throw new IllegalArgumentException(youTubeVideo.toString() + " is not a placeholder for a redirected Spotify Track");
        }

        // early exit to avoid duplicate loading of Playables that have been loaded prioritised by invoking Playable#fetchNow
        if (youTubeVideo.isDone()) {
            return;
        }

        youTubeVideo.markLoading();
        Optional<SpotifyRedirectIndex> persistedSpotifyRedirectIndex = queryExistingIndex(session, spotifyTrack.getId());

        if (persistedSpotifyRedirectIndex.isPresent()) {
            SpotifyRedirectIndex spotifyRedirectIndex = persistedSpotifyRedirectIndex.get();
            YouTubeVideo video = youTubeService.getVideoForId(spotifyRedirectIndex.getYouTubeId());
            if (video != null) {
                try {
                    youTubeVideo.setId(video.getVideoId());
                    youTubeVideo.setDuration(video.getDuration());
                } catch (UnavailableResourceException e) {
                    // never happens for YouTubeVideoImpl instances
                    throw new RuntimeException(e);
                }
                String name = spotifyTrack.getName();
                String artistString = StringList.create(spotifyTrack.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
                String title = String.format("%s by %s", name, artistString);
                youTubeVideo.setTitle(title);

                runUpdateTask(otherThreadSession -> {
                    spotifyRedirectIndex.setLastUsed(LocalDate.now());
                    otherThreadSession.update(spotifyRedirectIndex);
                });
                return;
            } else {
                runUpdateTask(otherThreadSession -> {
                    Object mergedIndex = otherThreadSession.merge(spotifyRedirectIndex);
                    otherThreadSession.delete(mergedIndex);
                });
            }
        }

        youTubeService.redirectSpotify(youTubeVideo);
        if (!youTubeVideo.isCanceled() && !Strings.isNullOrEmpty(spotifyTrack.getId())) {
            SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
                try {
                    String videoId = youTubeVideo.getVideoId();
                    SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(spotifyTrack.getId(), videoId);
                    // check again if the index was not created by other thread
                    if (queryExistingIndex(otherThreadSession, spotifyTrack.getId()).isEmpty()) {
                        invoker.invoke(() -> otherThreadSession.persist(spotifyRedirectIndex));
                    }
                } catch (UnavailableResourceException e) {
                    logger.warn("Tried creating a SpotifyRedirectIndex for an unavailable Track");
                } catch (Throwable e) {
                    logger.error("Exception while creating SpotifyRedirectIndex", e);
                }
            }));
        }
    }

    private void runUpdateTask(Consumer<Session> sessionConsumer) {
        SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
            Long modificationLocks = otherThreadSession
                .createQuery("select count(*) from " + SpotifyRedirectIndexModificationLock.class.getName(), Long.class)
                .uniqueResult();
            if (modificationLocks == 0) {
                invoker.invoke(sessionConsumer);
            }
        }));
    }

}

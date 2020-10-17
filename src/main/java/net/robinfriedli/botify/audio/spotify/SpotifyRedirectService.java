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
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.entities.SpotifyRedirectIndex;
import net.robinfriedli.botify.entities.SpotifyRedirectIndexModificationLock;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.MutexSync;
import net.robinfriedli.exec.modes.MutexSyncMode;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;

import static net.robinfriedli.botify.entities.SpotifyRedirectIndex.*;

/**
 * Service that aids loading the corresponding YouTube video for a Spotify track since Spotify does not allow playback
 * of full tracks via its api. Checks if there is a persisted {@link SpotifyRedirectIndex} or loads the YouTube video
 * via {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)} if not.
 */
public class SpotifyRedirectService {

    private static final ExecutorService SINGE_THREAD_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    private static final MutexSync<String> MUTEX_SYNC = new MutexSync<>();

    private final HibernateInvoker invoker = HibernateInvoker.create();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Session session;
    private final YouTubeService youTubeService;

    public SpotifyRedirectService(Session session, YouTubeService youTubeService) {
        this.session = session;
        this.youTubeService = youTubeService;
    }

    public void redirectTrack(HollowYouTubeVideo youTubeVideo) throws IOException {
        Track spotifyTrack = youTubeVideo.getRedirectedSpotifyTrack();

        if (spotifyTrack == null) {
            throw new IllegalArgumentException(youTubeVideo.toString() + " is not a placeholder for a redirected Spotify Track");
        }

        String spotifyTrackId = spotifyTrack.getId();
        Optional<SpotifyRedirectIndex> persistedSpotifyRedirectIndex;
        if (spotifyTrackId != null) {
            persistedSpotifyRedirectIndex = MUTEX_SYNC.evaluate(
                spotifyTrackId,
                () -> queryExistingIndex(session, spotifyTrackId)
            );
        } else {
            persistedSpotifyRedirectIndex = Optional.empty();
        }

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

                Mode mode = Mode.create().with(new MutexSyncMode<>(spotifyTrackId, MUTEX_SYNC));
                runUpdateTask(mode, otherThreadSession -> {
                    spotifyRedirectIndex.setLastUsed(LocalDate.now());
                    otherThreadSession.update(spotifyRedirectIndex);
                });
                return;
            } else {
                Mode mode = Mode.create().with(new MutexSyncMode<>(spotifyTrackId, MUTEX_SYNC));
                runUpdateTask(mode, otherThreadSession -> {
                    Long existingCount = otherThreadSession.createQuery(
                        "select count(*) from " + SpotifyRedirectIndex.class.getName()
                            + " where pk = " + spotifyRedirectIndex.getPk(),
                        Long.class
                    ).uniqueResult();

                    if (existingCount != 0) {
                        otherThreadSession.delete(otherThreadSession.merge(spotifyRedirectIndex));
                    }
                });
            }
        }

        youTubeService.redirectSpotify(youTubeVideo);
        if (!youTubeVideo.isCanceled() && !Strings.isNullOrEmpty(spotifyTrackId)) {
            SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
                try {
                    String videoId = youTubeVideo.getVideoId();
                    SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(spotifyTrackId, videoId);
                    // check again if the index was not created by other thread
                    if (queryExistingIndex(otherThreadSession, spotifyTrackId).isEmpty()) {
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

    private void runUpdateTask(Mode mode, Consumer<Session> sessionConsumer) {
        SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
            Long modificationLocks = otherThreadSession
                .createQuery("select count(*) from " + SpotifyRedirectIndexModificationLock.class.getName(), Long.class)
                .uniqueResult();
            if (modificationLocks == 0) {
                invoker.invoke(mode, () -> sessionConsumer.accept(otherThreadSession));
            }
        }));
    }

}

package net.robinfriedli.botify.cron.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.entities.SpotifyRedirectIndex;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.exec.Invoker;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.quartz.JobExecutionContext;

/**
 * Task that refreshes SpotifyRedirectIndices that have not been updated in the last 2 weeks
 */
public class RefreshSpotifyRedirectIndicesTask extends AbstractCronTask {

    private final SpotifyApi spotifyApi;
    private final Logger logger;

    public RefreshSpotifyRedirectIndicesTask() {
        spotifyApi = Botify.get().getSpotifyApiBuilder().build();
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        Botify botify = Botify.get();
        Stopwatch stopwatch = Stopwatch.createStarted();
        YouTubeService youTubeService = botify.getAudioManager().getYouTubeService();
        SpotifyTrackBulkLoadingService spotifyTrackBulkLoadingService = new SpotifyTrackBulkLoadingService(spotifyApi);

        LocalDate currentDate = LocalDate.now();
        LocalDate date2WeeksAgo = currentDate.minusDays(14);

        StaticSessionProvider.invokeWithSession(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<SpotifyRedirectIndex> query = cb.createQuery(SpotifyRedirectIndex.class);
            Root<SpotifyRedirectIndex> root = query.from(SpotifyRedirectIndex.class);
            query.where(cb.lessThan(root.get("lastUpdated"), date2WeeksAgo));
            List<SpotifyRedirectIndex> indices = session.createQuery(query).getResultList();

            RegenerateSpotifyRedirectIndexTask task = new RegenerateSpotifyRedirectIndexTask(session, youTubeService);
            int updateCount = 0;
            for (SpotifyRedirectIndex index : indices) {
                session.delete(index);
                spotifyTrackBulkLoadingService.add(index.getSpotifyId(), task);
                ++updateCount;
            }
            // make sure the deletion of the old indices are flushed to the database before creating the new indices
            // so as to not violate the unique constraints
            session.flush();
            spotifyTrackBulkLoadingService.perform();

            stopwatch.stop();
            logger.info(String.format("Regenerated %s spotify redirect indices in %s seconds", updateCount, stopwatch.elapsed(TimeUnit.SECONDS)));
        });
    }

    @Override
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create().with(new HibernateTransactionMode()).with(new SpotifyAuthorizationMode(spotifyApi));
    }

    private class RegenerateSpotifyRedirectIndexTask implements Consumer<Track> {

        private final Session session;
        private final YouTubeService youTubeService;

        private RegenerateSpotifyRedirectIndexTask(Session session, YouTubeService youTubeService) {
            this.session = session;
            this.youTubeService = youTubeService;
        }

        @Override
        public void accept(Track track) {
            try {
                HollowYouTubeVideo hollowYouTubeVideo = new HollowYouTubeVideo(youTubeService, track);
                youTubeService.redirectSpotify(hollowYouTubeVideo);
                String videoId = hollowYouTubeVideo.getVideoId();
                SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(track.getId(), videoId);
                session.persist(spotifyRedirectIndex);
            } catch (Throwable e) {
                Throwable errorCause = e;
                boolean isUniqueConstraintException = false;
                do {
                    if (errorCause instanceof ConstraintViolationException) {
                        isUniqueConstraintException = true;
                        break;
                    }
                    errorCause = errorCause.getCause();
                } while (errorCause != null);
                if (isUniqueConstraintException) {
                    logger.warn(String.format("Encountered constraint violation while updating redirect index for track '%s', index was probably created in other thread.",
                        track.getId()));
                } else {
                    logger.error("Exception while updating SpotifyRedirectIndex", e);
                }
            }
        }
    }

}

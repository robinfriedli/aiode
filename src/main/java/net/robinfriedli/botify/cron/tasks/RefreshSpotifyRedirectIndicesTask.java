package net.robinfriedli.botify.cron.tasks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.entities.SpotifyRedirectIndex;
import net.robinfriedli.botify.entities.SpotifyRedirectIndexModificationLock;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.exec.Invoker;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
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
        logger.info("Starting SpotifyRedirectIndex refresh");
        SessionFactory sessionFactory = StaticSessionProvider.getSessionFactory();
        SpotifyRedirectIndexModificationLock spotifyRedirectIndexModificationLock = new SpotifyRedirectIndexModificationLock();
        spotifyRedirectIndexModificationLock.setCreationTimeStamp(LocalDateTime.now());
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(spotifyRedirectIndexModificationLock);
            transaction.commit();
        }

        try {
            Botify botify = Botify.get();
            Stopwatch stopwatch = Stopwatch.createStarted();
            YouTubeService youTubeService = botify.getAudioManager().getYouTubeService();
            SpotifyTrackBulkLoadingService spotifyTrackBulkLoadingService = new SpotifyTrackBulkLoadingService(spotifyApi, true);

            LocalDate currentDate = LocalDate.now();
            LocalDate date2WeeksAgo = currentDate.minusDays(28);

            StaticSessionProvider.invokeWithSession(session -> {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaQuery<SpotifyRedirectIndex> query = cb.createQuery(SpotifyRedirectIndex.class);
                Root<SpotifyRedirectIndex> root = query.from(SpotifyRedirectIndex.class);
                query.where(cb.lessThan(root.get("lastUpdated"), date2WeeksAgo));
                query.orderBy(cb.asc(root.get("lastUpdated")));
                List<SpotifyRedirectIndex> indices = session.createQuery(query).setLockOptions(new LockOptions(LockMode.PESSIMISTIC_WRITE)).getResultList();

                if (indices.isEmpty()) {
                    return;
                }

                BigDecimal averageDailyIndices = (BigDecimal) session
                    .createSQLQuery("select avg(count) from (select count(*) as count from spotify_redirect_index group by last_updated) as sub")
                    .uniqueResult();
                int average = averageDailyIndices.setScale(0, RoundingMode.CEILING).intValue();

                int updateCount = 0;
                for (SpotifyRedirectIndex index : indices) {
                    RefreshTrackIndexTask task = new RefreshTrackIndexTask(session, index, youTubeService);
                    String spotifyId = index.getSpotifyId();
                    if (!Strings.isNullOrEmpty(spotifyId)) {
                        spotifyTrackBulkLoadingService.add(spotifyId, task);
                    } else {
                        session.delete(index);
                    }
                    ++updateCount;

                    if (updateCount == average) {
                        break;
                    }
                }

                spotifyTrackBulkLoadingService.perform();

                stopwatch.stop();
                logger.info(String.format("Regenerated %s spotify redirect indices in %s seconds", updateCount, stopwatch.elapsed(TimeUnit.SECONDS)));
            });
        } finally {
            try (Session session = sessionFactory.openSession()) {
                Transaction transaction = session.beginTransaction();
                session.delete(spotifyRedirectIndexModificationLock);
                transaction.commit();
            } catch (Throwable e) {
                // catch exceptions thrown in the finally block so as to not override exceptions thrown in the try block
                logger.error("Exception thrown while deleting SpotifyRedirectIndexModificationLock", e);
            }
        }
    }

    @Override
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create().with(new HibernateTransactionMode()).with(new SpotifyAuthorizationMode(spotifyApi));
    }

    private class RefreshTrackIndexTask implements Consumer<Track> {

        private final Session session;
        private final SpotifyRedirectIndex index;
        private final YouTubeService youTubeService;

        private RefreshTrackIndexTask(Session session, SpotifyRedirectIndex index, YouTubeService youTubeService) {
            this.session = session;
            this.index = index;
            this.youTubeService = youTubeService;
        }

        @Override
        public void accept(Track track) {
            try {
                if (track == null) {
                    session.delete(index);
                    return;
                }

                HollowYouTubeVideo hollowYouTubeVideo = new HollowYouTubeVideo(youTubeService, track);
                try {
                    youTubeService.redirectSpotify(hollowYouTubeVideo);
                } catch (CommandRuntimeException e) {
                    throw e.getCause();
                }
                if (!hollowYouTubeVideo.isCanceled() && !Strings.isNullOrEmpty(track.getId())) {
                    String videoId = hollowYouTubeVideo.getVideoId();
                    index.setYouTubeId(videoId);
                    index.setLastUpdated(LocalDate.now());
                } else {
                    session.delete(index);
                }
            } catch (UnavailableResourceException e) {
                logger.warn("Tried creating a SpotifyRedirectIndex for an unavailable Video for track id " + track.getId());
            } catch (GoogleJsonResponseException e) {
                String message = e.getDetails().getMessage();
                logger.error(String.format("GoogleJsonResponse exception while refreshing index for track id %s: %s", track.getId(), message));
            } catch (Throwable e) {
                logger.error("Exception while updating SpotifyRedirectIndex for track id " + track.getId(), e);
            }
        }
    }

}

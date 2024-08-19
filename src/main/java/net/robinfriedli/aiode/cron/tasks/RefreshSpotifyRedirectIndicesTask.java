package net.robinfriedli.aiode.cron.tasks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.entities.SpotifyRedirectIndex;
import net.robinfriedli.aiode.entities.SpotifyRedirectIndexModificationLock;
import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.function.modes.HibernateTransactionMode;
import net.robinfriedli.aiode.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.quartz.JobExecutionContext;
import se.michaelthelin.spotify.SpotifyApi;

import static net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService.*;

/**
 * Task that refreshes SpotifyRedirectIndices that have not been updated in the last 2 weeks
 */
public class RefreshSpotifyRedirectIndicesTask extends AbstractCronTask {

    private final SpotifyApi spotifyApi;
    private final Logger logger;

    public RefreshSpotifyRedirectIndicesTask() {
        spotifyApi = Aiode.get().getSpotifyApiBuilder().build();
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
            Aiode aiode = Aiode.get();
            Stopwatch stopwatch = Stopwatch.createStarted();
            YouTubeService youTubeService = aiode.getAudioManager().getYouTubeService();
            SpotifyTrackBulkLoadingService spotifyTrackBulkLoadingService = new SpotifyTrackBulkLoadingService(spotifyApi, true);

            LocalDate currentDate = LocalDate.now();
            LocalDate date4WeeksAgo = currentDate.minusDays(28);

            StaticSessionProvider.consumeSession(session -> {
                SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(aiode.getFilebrokerApi(), session, youTubeService);
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaQuery<SpotifyRedirectIndex> query = cb.createQuery(SpotifyRedirectIndex.class);
                Root<SpotifyRedirectIndex> root = query.from(SpotifyRedirectIndex.class);
                query.where(cb.lessThan(root.get("lastUpdated"), date4WeeksAgo));
                query.orderBy(cb.asc(root.get("lastUpdated")));
                List<SpotifyRedirectIndex> indices = session.createQuery(query).setLockOptions(new LockOptions(LockMode.PESSIMISTIC_WRITE)).getResultList();

                if (indices.isEmpty()) {
                    return;
                }

                BigDecimal averageDailyIndices = session
                    .createNativeQuery("select avg(count) from (select count(*) as count from spotify_redirect_index group by last_updated) as sub", BigDecimal.class)
                    .uniqueResult();
                int average = averageDailyIndices.setScale(0, RoundingMode.CEILING).intValue();

                int updateCount = 0;
                for (SpotifyRedirectIndex index : indices) {
                    SpotifyTrackKind kind = index.getSpotifyItemKind().asEnum();
                    RefreshTrackIndexTask task = new RefreshTrackIndexTask(session, index, spotifyRedirectService, youTubeService);
                    String spotifyId = index.getSpotifyId();
                    if (!Strings.isNullOrEmpty(spotifyId)) {
                        spotifyTrackBulkLoadingService.add(createItem(spotifyId, kind), task);
                    } else {
                        session.remove(index);
                    }
                    ++updateCount;

                    if (updateCount == average) {
                        break;
                    }
                }

                spotifyTrackBulkLoadingService.perform();

                stopwatch.stop();
                logger.info(String.format("Regenerated %d spotify redirect indices in %d seconds", updateCount, stopwatch.elapsed(TimeUnit.SECONDS)));
            });
        } finally {
            Transaction transaction = null;
            try (Session session = sessionFactory.openSession()) {
                transaction = session.beginTransaction();
                // since hibernate is now bootstrapped by JPA rather than native after implementing spring boot
                // the entity has to be merged because JPA does not allow the deletion of detached entities
                Object merge = session.merge(spotifyRedirectIndexModificationLock);
                session.remove(merge);
                transaction.commit();
            } catch (Throwable e) {
                // catch exceptions thrown in the finally block so as to not override exceptions thrown in the try block
                logger.error("Exception thrown while deleting SpotifyRedirectIndexModificationLock", e);
                if (transaction != null) {
                    transaction.rollback();
                }
            }
        }
    }

    @Override
    protected Mode getMode() {
        return Mode.create().with(new HibernateTransactionMode()).with(new SpotifyAuthorizationMode(spotifyApi));
    }

    private class RefreshTrackIndexTask implements Consumer<SpotifyTrack> {

        private final Session session;
        private final SpotifyRedirectIndex index;
        private final SpotifyRedirectService spotifyRedirectService;
        private final YouTubeService youTubeService;

        private RefreshTrackIndexTask(Session session, SpotifyRedirectIndex index, SpotifyRedirectService spotifyRedirectService, YouTubeService youTubeService) {
            this.session = session;
            this.index = index;
            this.spotifyRedirectService = spotifyRedirectService;
            this.youTubeService = youTubeService;
        }

        @Override
        public void accept(SpotifyTrack track) {
            try {
                if (track == null) {
                    session.remove(index);
                    return;
                }

                LocalDate date2WeeksAgo = LocalDate.now().minusDays(14);
                if (index.getLastUsed().isBefore(date2WeeksAgo)) {
                    session.remove(index);
                } else {
                    FilebrokerApi.Post post = spotifyRedirectService.findFilebrokerPostForSpotifyTrack(track);

                    if (post != null) {
                        index.setFileBrokerPk(post.getPk());
                    } else {
                        index.setFileBrokerPk(null);
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
                            session.remove(index);
                        }
                    }
                }
            } catch (UnavailableResourceException e) {
                logger.warn("Tried creating a SpotifyRedirectIndex for an unavailable Video for track id " + track.getId());
            } catch (GoogleJsonResponseException e) {
                String message = e.getDetails().getMessage();
                logger.error(String.format("GoogleJsonResponse exception while refreshing index for track id %s: %s", track.getId(), message));
            } catch (Throwable e) {
                logger.error(String.format("Exception while updating SpotifyRedirectIndex for track id %s", track != null ? track.getId() : null), e);
            }
        }
    }

}

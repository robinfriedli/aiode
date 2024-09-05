package net.robinfriedli.aiode.audio.spotify;

import java.io.IOException;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.text.similarity.LevenshteinDistance;

import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioTrackLoader;
import net.robinfriedli.aiode.audio.UrlPlayable;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.boot.ShutdownableExecutorService;
import net.robinfriedli.aiode.concurrent.LoggingThreadFactory;
import net.robinfriedli.aiode.entities.SpotifyRedirectIndex;
import net.robinfriedli.aiode.entities.SpotifyRedirectIndexModificationLock;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.function.HibernateInvoker;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.hibernate.Session;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;

import static net.robinfriedli.aiode.entities.SpotifyRedirectIndex.*;

/**
 * Service that aids loading the corresponding YouTube video for a Spotify track since Spotify does not allow playback
 * of full tracks via its api. Checks if there is a persisted {@link SpotifyRedirectIndex} or loads the YouTube video
 * via {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)} if not.
 */
public class SpotifyRedirectService {

    private static final ExecutorService SINGE_THREAD_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(new LoggingThreadFactory("spotify-redirect-service-pool"));

    static {
        Aiode.SHUTDOWNABLES.add(new ShutdownableExecutorService(SINGE_THREAD_EXECUTOR_SERVICE));
    }

    private final FilebrokerApi filebrokerApi;
    private final HibernateInvoker invoker;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Session session;
    private final YouTubeService youTubeService;

    public SpotifyRedirectService(FilebrokerApi filebrokerApi, Session session, YouTubeService youTubeService) {
        this.filebrokerApi = filebrokerApi;
        invoker = HibernateInvoker.create();
        this.session = session;
        this.youTubeService = youTubeService;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void redirectTrack(SpotifyTrackRedirect spotifyTrackRedirect) throws IOException {
        synchronized (spotifyTrackRedirect) {
            if (spotifyTrackRedirect.isDone()) {
                return;
            }

            try {
                spotifyTrackRedirect.markLoading();
                SpotifyTrack spotifyTrack = spotifyTrackRedirect.getSpotifyTrack();
                String spotifyTrackId = spotifyTrack.getId();
                Optional<SpotifyRedirectIndex> persistedSpotifyRedirectIndex;
                if (!Strings.isNullOrEmpty(spotifyTrackId)) {
                    persistedSpotifyRedirectIndex = queryExistingIndex(session, spotifyTrackId);
                } else {
                    persistedSpotifyRedirectIndex = Optional.empty();
                }

                if (persistedSpotifyRedirectIndex.isPresent() && persistedSpotifyRedirectIndex.get().getFileBrokerPk() != null) {
                    Long fileBrokerPk = persistedSpotifyRedirectIndex.get().getFileBrokerPk();
                    try {
                        FilebrokerApi.PostDetailed post = filebrokerApi.getPostAsync(fileBrokerPk, null, null).get(10, TimeUnit.SECONDS);
                        spotifyTrackRedirect.complete(new FilebrokerPlayableWrapper(new FilebrokerApi.Post(post)));
                        runUpdateTask(spotifyTrackId, (index, session) -> index.setLastUsed(LocalDate.now()));
                        return;
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof FilebrokerApi.InvalidHttpResponseException filebrokerApiException && filebrokerApiException.getStatus() == 403) {
                            logger.warn("Filebroker post for redirected spotify track {} has become unavailable", spotifyTrackId);
                            runUpdateTask(spotifyTrackId, (track, session) -> track.setFileBrokerPk(null));
                        } else {
                            logger.error("Failed to load filebroker post for redirected spotify track " + spotifyTrackId, e);
                        }
                    }
                }

                FilebrokerApi.Post post = findFilebrokerPostForSpotifyTrack(spotifyTrack);
                if (post == null) {
                    AudioTrack soundCloudTrack = null;
                    if (persistedSpotifyRedirectIndex.isPresent() && !Strings.isNullOrEmpty(persistedSpotifyRedirectIndex.get().getSoundCloudUri())) {
                        try {
                            AudioTrackLoader audioTrackLoader = new AudioTrackLoader(Aiode.get().getAudioManager().getPlayerManager());
                            soundCloudTrack = (AudioTrack) audioTrackLoader.loadByIdentifier(persistedSpotifyRedirectIndex.get().getSoundCloudUri());
                        } catch (Exception e) {
                            logger.error("Failed to load soundcloud track with uri {} for track {}", persistedSpotifyRedirectIndex.get().getSoundCloudUri(), spotifyTrackId, e);
                        }
                    }
                    if (soundCloudTrack == null) {
                        // if the index already exists, only try to find a soundcloud track again if the last time the index was updated was before soundcloud search was implemented
                        if (persistedSpotifyRedirectIndex.isEmpty() || persistedSpotifyRedirectIndex.get().getLastUpdated().isBefore(LocalDate.of(2024, Month.SEPTEMBER, 7))) {
                            soundCloudTrack = findSoundCloudTrackForSpotifyTrack(spotifyTrack);
                        }
                    }
                    redirectTrackToYouTube(spotifyTrackRedirect.getYouTubeVideo());
                    if (soundCloudTrack == null) {
                        spotifyTrackRedirect.complete(null);
                    } else {
                        String soundCloudUri = soundCloudTrack.getInfo().uri;
                        if (persistedSpotifyRedirectIndex.isPresent()) {
                            runUpdateTask(spotifyTrackId, (index, session) -> {
                                LocalDate now = LocalDate.now();
                                index.setLastUsed(now);
                                index.setLastUpdated(now);
                                index.setSoundCloudUri(soundCloudUri);
                            });
                        } else {
                            SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
                                try {
                                    SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(spotifyTrack.getId(), null, null, soundCloudUri, spotifyTrack.getKind(), otherThreadSession);
                                    // check again if the index was not created by other thread
                                    Optional<SpotifyRedirectIndex> createdIndex = queryExistingIndex(otherThreadSession, spotifyTrackId);
                                    if (createdIndex.isEmpty()) {
                                        invoker.invoke(() -> otherThreadSession.persist(spotifyRedirectIndex));
                                    } else {
                                        runUpdateTask(spotifyTrackId, (index, session) -> {
                                            LocalDate now = LocalDate.now();
                                            index.setLastUsed(now);
                                            index.setLastUpdated(now);
                                            index.setSoundCloudUri(soundCloudUri);
                                        });
                                    }
                                } catch (Exception e) {
                                    logger.error("Exception while creating SpotifyRedirectIndex", e);
                                }
                            }));
                        }
                        UrlPlayable soundCloudPlayable = new UrlPlayable(soundCloudTrack);
                        spotifyTrackRedirect.complete(soundCloudPlayable);
                    }
                    return;
                }

                if (persistedSpotifyRedirectIndex.isPresent()) {
                    runUpdateTask(spotifyTrackId, (index, session) -> {
                        LocalDate now = LocalDate.now();
                        index.setLastUsed(now);
                        index.setLastUpdated(now);
                        index.setFileBrokerPk(post.getPk());
                    });
                } else {
                    SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
                        try {
                            SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(spotifyTrack.getId(), null, post.getPk(), null, spotifyTrack.getKind(), otherThreadSession);
                            // check again if the index was not created by other thread
                            if (queryExistingIndex(otherThreadSession, spotifyTrackId).isEmpty()) {
                                invoker.invoke(() -> otherThreadSession.persist(spotifyRedirectIndex));
                            }
                        } catch (Exception e) {
                            logger.error("Exception while creating SpotifyRedirectIndex", e);
                        }
                    }));
                }

                spotifyTrackRedirect.complete(new FilebrokerPlayableWrapper(post));
            } catch (InterruptedException | TimeoutException e) {
                throw new RemoteException("Failed to load track redirect for spotify track " + spotifyTrackRedirect.getSpotifyTrack().getId(), e);
            } finally {
                if (!spotifyTrackRedirect.isDone()) {
                    spotifyTrackRedirect.complete(null);
                }
            }
        }
    }

    @Nullable
    public FilebrokerApi.Post findFilebrokerPostForSpotifyTrack(SpotifyTrack spotifyTrack) throws InterruptedException, TimeoutException {
        StringBuilder filebrokerQueryBuilder = new StringBuilder("(@type ~= \"audio\" OR @type ~= \"video\" OR music)");
        String trackName = spotifyTrack.getName();
        filebrokerQueryBuilder.append(" (@title ~= \"").append(trackName.replaceAll("[\"\\\\]", "_")).append("%\"");
        String trackNameWithParenthesisRemoved = trackName.replaceAll("\\(.*\\)|\\[.*]", "").trim().replaceAll("\\s+", " ");
        String trackNameWithHyphenRemoved = trackName.split("-")[0].trim();
        if (!trackNameWithParenthesisRemoved.isEmpty() && !trackNameWithParenthesisRemoved.equals(trackName)) {
            filebrokerQueryBuilder.append(" OR @title ~= \"").append(trackNameWithParenthesisRemoved.replaceAll("[\"\\\\]", "_")).append("%\"");
        }
        if (!trackNameWithHyphenRemoved.isEmpty() && !trackNameWithHyphenRemoved.equals(trackName)) {
            filebrokerQueryBuilder.append(" OR @title ~= \"").append(trackNameWithHyphenRemoved.replaceAll("[\"\\\\]", "_")).append("%\"");
        }
        filebrokerQueryBuilder.append(")");
        String[] artists = spotifyTrack.exhaustiveMatch(
            track -> Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).toArray(String[]::new),
            episode -> Optional.ofNullable(episode.getShow()).map(show -> new String[]{show.getName()}).orElse(new String[0])
        );
        if (artists.length > 0) {
            filebrokerQueryBuilder.append(" (");
            for (int i = 0; i < artists.length; i++) {
                filebrokerQueryBuilder.append("@artist ~= \"%").append(artists[i].replaceAll("[\"\\\\]", "_")).append("%\"");
                if (i < artists.length - 1) {
                    filebrokerQueryBuilder.append(" OR ");
                }
            }
            filebrokerQueryBuilder.append(")");
        }

        FilebrokerApi.SearchResult searchResult;
        List<FilebrokerApi.Post> posts;

        try {
            searchResult = filebrokerApi.searchPostsAsync(filebrokerQueryBuilder.toString(), null, null).get(10, TimeUnit.SECONDS);
            posts = searchResult.getPosts();
        } catch (ExecutionException e) {
            logger.error("Failed to execute filebroker query to redirect spotify track " + spotifyTrack.getId(), e);
            posts = null;
        }

        if (posts == null || posts.isEmpty()) {
            return null;
        }

        LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
        Map<Integer, FilebrokerApi.Post> postsByLevenshteinDistance = new HashMap<>();
        for (FilebrokerApi.Post post : posts) {
            Integer titleDistance = levenshteinDistance.apply(trackName, post.getTitle());
            int artistDistance = Arrays.stream(artists).mapToInt(artist ->
                levenshteinDistance.apply(
                    artist,
                    Objects.requireNonNullElse(post.getS3_object_metadata().getArtist(), "")
                )
            ).min().orElseGet(() -> Objects.requireNonNullElse(post.getS3_object_metadata().getArtist(), "").length());
            String album = spotifyTrack.exhaustiveMatch(
                track -> Optional.ofNullable(track.getAlbum()).map(AlbumSimplified::getName).orElse(""),
                episode -> Optional.ofNullable(episode.getShow()).map(ShowSimplified::getName).orElse("")
            );
            Integer albumDistance = levenshteinDistance.apply(album, Objects.requireNonNullElse(post.getS3_object_metadata().getAlbum(), ""));

            postsByLevenshteinDistance.put(titleDistance + artistDistance + albumDistance, post);
        }
        int bestScore = postsByLevenshteinDistance.keySet().stream().mapToInt(Integer::intValue).min().getAsInt();

        return postsByLevenshteinDistance.get(bestScore);
    }

    @Nullable
    public AudioTrack findSoundCloudTrackForSpotifyTrack(SpotifyTrack spotifyTrack) {
        String trackName = spotifyTrack.getName();
        String trackNameWithParenthesisRemoved = trackName.replaceAll("\\(.*\\)|\\[.*]", "").trim().replaceAll("\\s+", " ").toLowerCase();
        String trackNameWithHyphenRemoved = trackName.split("-")[0].trim().toLowerCase();
        String[] artists = spotifyTrack.exhaustiveMatch(
            track -> Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).toArray(String[]::new),
            episode -> Optional.ofNullable(episode.getShow()).map(show -> new String[]{show.getName()}).orElse(new String[0])
        );
        String artist = artists.length > 0 ? artists[0] : "";

        if ((Strings.isNullOrEmpty(trackName) || trackName.isBlank())) {
            return null;
        }

        AudioTrackLoader audioTrackLoader = new AudioTrackLoader(Aiode.get().getAudioManager().getPlayerManager());
        AudioItem audioItem = audioTrackLoader.loadByIdentifier(String.format("scsearch:%s %s", trackName, artist));
        if (!(audioItem instanceof AudioPlaylist audioPlaylist)) {
            return null;
        }

        List<AudioTrack> tracks = audioPlaylist.getTracks();
        LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
        Map<Integer, AudioTrack> tracksByLevenshteinDistance = new HashMap<>();
        for (AudioTrack track : tracks) {
            long duration = track.getDuration();
            if (Strings.isNullOrEmpty(track.getInfo().uri)
                || Strings.isNullOrEmpty(track.getInfo().title)
                || !(track.getInfo().title.toLowerCase().contains(trackName.toLowerCase()) || track.getInfo().title.contains(trackNameWithParenthesisRemoved) || track.getInfo().title.contains(trackNameWithHyphenRemoved))
                || !(track.getInfo().author != null && Arrays.stream(artists).anyMatch(a -> track.getInfo().author.toLowerCase().contains(a.toLowerCase())))
                || Math.abs(spotifyTrack.getDurationMs() - duration) > 10000) {
                continue;
            }

            Integer titleDistance = levenshteinDistance.apply(trackName, Objects.requireNonNullElse(track.getInfo().title, ""));

            String author = Objects.requireNonNullElse(track.getInfo().author, "");
            int artistDistance = Arrays.stream(artists).mapToInt(a -> levenshteinDistance.apply(a, author)).min().orElseGet(() -> levenshteinDistance.apply(artist, author));

            tracksByLevenshteinDistance.put(titleDistance + artistDistance, track);
        }
        if (tracksByLevenshteinDistance.isEmpty()) {
            return null;
        }

        int bestScore = tracksByLevenshteinDistance.keySet().stream().mapToInt(Integer::intValue).min().getAsInt();
        return tracksByLevenshteinDistance.get(bestScore);
    }

    public void redirectTrackToYouTube(HollowYouTubeVideo youTubeVideo) throws IOException {
        SpotifyTrack spotifyTrack = youTubeVideo.getRedirectedSpotifyTrack();

        if (spotifyTrack == null) {
            throw new IllegalArgumentException(youTubeVideo.toString() + " is not a placeholder for a redirected Spotify Track");
        }

        // early exit to avoid duplicate loading of Playables that have been loaded prioritised by invoking Playable#fetchNow
        if (youTubeVideo.isDone()) {
            return;
        }

        youTubeVideo.markLoading();
        String spotifyTrackId = spotifyTrack.getId();
        Optional<SpotifyRedirectIndex> persistedSpotifyRedirectIndex;
        if (!Strings.isNullOrEmpty(spotifyTrackId)) {
            persistedSpotifyRedirectIndex = queryExistingIndex(session, spotifyTrackId);
        } else {
            persistedSpotifyRedirectIndex = Optional.empty();
        }

        if (persistedSpotifyRedirectIndex.isPresent() && persistedSpotifyRedirectIndex.get().getYouTubeId() != null) {
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

                youTubeVideo.setTitle(spotifyTrack.getDisplay());

                runUpdateTask(spotifyTrackId, (index, session) -> index.setLastUsed(LocalDate.now()));
                return;
            } else {
                runUpdateTask(spotifyTrackId, (index, session) -> session.remove(index));
            }
        }

        youTubeService.redirectSpotify(youTubeVideo);
        if (!youTubeVideo.isCanceled() && !Strings.isNullOrEmpty(spotifyTrack.getId())) {
            if (persistedSpotifyRedirectIndex.isPresent()) {
                runUpdateTask(spotifyTrackId, (index, session) -> {
                    LocalDate now = LocalDate.now();
                    index.setLastUsed(now);
                    index.setLastUpdated(now);
                    try {
                        index.setYouTubeId(youTubeVideo.getVideoId());
                    } catch (UnavailableResourceException e) {
                        logger.warn("Tried creating a SpotifyRedirectIndex for an unavailable Track");
                    }
                });
            } else {
                SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(otherThreadSession -> {
                    try {
                        String videoId = youTubeVideo.getVideoId();
                        SpotifyRedirectIndex spotifyRedirectIndex = new SpotifyRedirectIndex(spotifyTrack.getId(), videoId, null, null, spotifyTrack.getKind(), otherThreadSession);
                        // check again if the index was not created by other thread
                        if (queryExistingIndex(otherThreadSession, spotifyTrackId).isEmpty()) {
                            invoker.invoke(() -> otherThreadSession.persist(spotifyRedirectIndex));
                        }
                    } catch (UnavailableResourceException e) {
                        logger.warn("Tried creating a SpotifyRedirectIndex for an unavailable Track");
                    } catch (Exception e) {
                        logger.error("Exception while creating SpotifyRedirectIndex", e);
                    }
                }));
            }
        }
    }

    private void runUpdateTask(String spotifyId, BiConsumer<SpotifyRedirectIndex, Session> sessionConsumer) {
        SINGE_THREAD_EXECUTOR_SERVICE.execute(() -> StaticSessionProvider.consumeSession(session -> {
            Long modificationLocks = session
                .createQuery("select count(*) from " + SpotifyRedirectIndexModificationLock.class.getName(), Long.class)
                .uniqueResult();
            if (modificationLocks == 0) {
                Optional<SpotifyRedirectIndex> foundIndex = queryExistingIndex(session, spotifyId);
                foundIndex.ifPresent(spotifyRedirectIndex -> sessionConsumer.accept(spotifyRedirectIndex, session));
            }
        }));
    }

}

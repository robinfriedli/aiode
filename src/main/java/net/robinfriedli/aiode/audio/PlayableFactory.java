package net.robinfriedli.aiode.audio;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.hc.core5.http.ParseException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.robinfriedli.aiode.audio.exec.SpotifyTrackRedirectionRunnable;
import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.aiode.audio.exec.YouTubePlaylistPopulationRunnable;
import net.robinfriedli.aiode.audio.spotify.PlayableTrackWrapper;
import net.robinfriedli.aiode.audio.spotify.SpotifyService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.entities.UrlTrack;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.function.CheckedFunction;
import net.robinfriedli.aiode.function.SpotifyInvoker;
import net.robinfriedli.stringlist.StringList;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

/**
 * Factory class for {@link Playable}. Instantiates the matching Playable implementation for given Object or URL
 * and handles populating YouTube playlists and redirecting Spotify tracks asynchronously
 */
public class PlayableFactory {

    private final AudioTrackLoader audioTrackLoader;
    private final SpotifyService spotifyService;
    private final SpotifyInvoker invoker;
    private final TrackLoadingExecutor trackLoadingExecutor;
    private final YouTubeService youTubeService;

    public PlayableFactory(AudioTrackLoader audioTrackLoader, SpotifyService spotifyService, TrackLoadingExecutor trackLoadingExecutor, YouTubeService youTubeService) {
        this.audioTrackLoader = audioTrackLoader;
        this.spotifyService = spotifyService;
        invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
        this.trackLoadingExecutor = trackLoadingExecutor;
        this.youTubeService = youTubeService;
    }

    /**
     * Creates a single playable for given Object, YouTube video or Spotify track, and redirects the Spotify track if
     * necessary.
     *
     * @param redirectSpotify if true the matching YouTube video is loaded to play the full track using
     *                        {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}, else a {@link PlayableTrackWrapper} is created to play the
     *                        preview mp3 provided by Spotify
     */
    public Playable createPlayable(boolean redirectSpotify, Object track) {
        List<Playable> playables = createPlayables(redirectSpotify, Collections.singleton(track));

        if (playables.isEmpty()) {
            return null;
        } else if (playables.size() == 1) {
            return playables.get(0);
        } else {
            throw new IllegalStateException(String.format("Expected 1 but found %s playables", playables.size()));
        }
    }

    public List<Playable> createPlayables(boolean redirectSpotify, Object item) {
        if (item instanceof Collection) {
            return createPlayables(redirectSpotify, (Collection) item);
        } else {
            return createPlayables(redirectSpotify, Lists.newArrayList(item));
        }
    }

    /**
     * Creates Playables for a Collection of Objects; YouTube videos or Spotify Tracks.
     *
     * @param redirectSpotify if true the matching YouTube video is loaded to play the full track using
     *                        {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}, else a {@link PlayableTrackWrapper} is created to play the
     *                        preview mp3 provided by Spotify
     * @param items           the objects to create a Playable for
     * @return the created Playables
     */
    public List<Playable> createPlayables(boolean redirectSpotify, Collection<?> items) {
        List<Playable> playables = Lists.newArrayList();
        List<HollowYouTubeVideo> tracksToRedirect = Lists.newArrayList();
        List<YouTubePlaylist> youTubePlaylistsToLoad = Lists.newArrayList();

        try {
            for (Object item : items) {
                if (item instanceof Playable) {
                    playables.add((Playable) item);
                } else if (item instanceof Track) {
                    handleTrack(SpotifyTrack.wrap((Track) item), redirectSpotify, tracksToRedirect, playables);
                } else if (item instanceof Episode) {
                    handleTrack(SpotifyTrack.wrap((Episode) item), redirectSpotify, tracksToRedirect, playables);
                } else if (item instanceof SpotifyTrack) {
                    handleTrack((SpotifyTrack) item, redirectSpotify, tracksToRedirect, playables);
                } else if (item instanceof UrlTrack) {
                    playables.add(((UrlTrack) item).asPlayable());
                } else if (item instanceof YouTubePlaylist) {
                    YouTubePlaylist youTubePlaylist = ((YouTubePlaylist) item);
                    playables.addAll(youTubePlaylist.getVideos());
                    youTubePlaylistsToLoad.add(youTubePlaylist);
                } else if (item instanceof PlaylistSimplified) {
                    List<SpotifyTrack> t = SpotifyInvoker.create(spotifyService.getSpotifyApi()).invoke(() -> spotifyService.getPlaylistTracks((PlaylistSimplified) item));
                    for (SpotifyTrack track : t) {
                        handleTrack(track, redirectSpotify, tracksToRedirect, playables);
                    }
                } else if (item instanceof AlbumSimplified) {
                    List<Track> t = invoker.invoke(() -> spotifyService.getAlbumTracks((AlbumSimplified) item));
                    for (Track track : t) {
                        handleTrack(SpotifyTrack.wrapIfNotNull(track), redirectSpotify, tracksToRedirect, playables);
                    }
                } else if (item instanceof AudioTrack) {
                    playables.add(new UrlPlayable((AudioTrack) item));
                } else if (item instanceof AudioPlaylist) {
                    List<Playable> convertedPlayables = ((AudioPlaylist) item).getTracks().stream().map(UrlPlayable::new).collect(Collectors.toList());
                    playables.addAll(convertedPlayables);
                } else if (item != null) {
                    throw new UnsupportedOperationException("Unsupported playable " + item.getClass());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while creating Playables", e);
        }

        if (!tracksToRedirect.isEmpty() || !youTubePlaylistsToLoad.isEmpty()) {
            trackLoadingExecutor.execute(new SpotifyTrackRedirectionRunnable(tracksToRedirect, youTubeService)
                .andThen(new YouTubePlaylistPopulationRunnable(youTubePlaylistsToLoad, youTubeService)));
        }

        return playables;
    }

    private void handleTrack(SpotifyTrack track, boolean redirectSpotify, List<HollowYouTubeVideo> tracksToRedirect, List<Playable> playables) {
        if (track == null) {
            return;
        }

        if (redirectSpotify) {
            HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, track);
            tracksToRedirect.add(youTubeVideo);
            playables.add(youTubeVideo);
        } else {
            playables.add(new PlayableTrackWrapper(track));
        }
    }

    /**
     * Populates a YouTube playlist by fetching the data for each {@link HollowYouTubeVideo} asynchronously and returning
     * them as Playables
     *
     * @param youTubePlaylist the YouTube playlist to load the videos for
     */
    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist) {
        List<Playable> playables = Lists.newArrayList(youTubePlaylist.getVideos());

        trackLoadingExecutor.execute(new YouTubePlaylistPopulationRunnable(youTubeService, youTubePlaylist));

        return playables;
    }

    /**
     * Create a single playable for any url. If the url is either an open.spotify or YouTube URL this extracts the ID
     * and uses the familiar methods to load the Playables, otherwise this method uses the {@link AudioTrackLoader}
     * to load the {@link AudioTrack}s using lavaplayer and wraps them in {@link UrlPlayable}s
     */
    @Nullable
    public Playable createPlayable(String url, SpotifyApi spotifyApi, boolean redirectSpotify) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException("'" + url + "' is not a valid URL");
        }
        if (uri.getHost().contains("youtube.com")) {
            Map<String, String> parameterMap = getParameterMap(uri);
            String videoId = parameterMap.get("v");
            if (videoId != null) {
                return youTubeService.getVideoForId(videoId);
            } else {
                throw new IllegalArgumentException("Detected YouTube URL but no video id provided");
            }
        } else if (uri.getHost().equals("youtu.be")) {
            String[] parts = uri.getPath().split("/");
            return youTubeService.requireVideoForId(parts[parts.length - 1]);
        } else if (uri.getHost().equals("open.spotify.com")) {
            StringList pathFragments = StringList.createWithRegex(uri.getPath(), "/");
            SpotifyTrackKind kind;
            String trackId;
            if (pathFragments.contains("track")) {
                trackId = pathFragments.tryGet(pathFragments.indexOf("track") + 1);
                kind = SpotifyTrackKind.TRACK;
            } else if (pathFragments.contains("episode")) {
                trackId = pathFragments.tryGet(pathFragments.indexOf("episode") + 1);
                kind = SpotifyTrackKind.EPISODE;
            } else {
                throw new IllegalArgumentException("Detected Spotify URL but no track id provided");
            }
            if (trackId == null) {
                throw new InvalidCommandException("No track id provided");
            }

            try {
                String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);
                if (kind == SpotifyTrackKind.TRACK) {
                    Track track = spotifyService.getTrack(trackId);
                    return createPlayable(redirectSpotify, track);
                } else //noinspection ConstantConditions
                    if (kind == SpotifyTrackKind.EPISODE) {
                        Episode episode = spotifyService.getEpisode(trackId);
                        return createPlayable(redirectSpotify, episode);
                    } else {
                        throw new UnsupportedOperationException("unsupported open.spotify URL kind: " + kind);
                    }
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                throw new RuntimeException("Exception during Spotify request", e);
            } finally {
                spotifyApi.setAccessToken(null);
            }
        } else {
            AudioItem audioItem = audioTrackLoader.loadByIdentifier(uri.toString());
            if (audioItem instanceof AudioTrack) {
                return new UrlPlayable((AudioTrack) audioItem);
            } else if (audioItem != null) {
                throw new IllegalArgumentException("Loading provided url did not result in an AudioTrack but " + audioItem.getClass().toString());
            } else {
                return null;
            }
        }
    }

    /**
     * Create Playables for any URL.
     *
     * @param url             the url that points to a playable track or playlist
     * @param spotifyApi      the SpotifyApi instance
     * @param redirectSpotify if true the loaded Spotify tracks will get directed to YouTube videos
     */
    public List<Playable> createPlayables(String url, SpotifyApi spotifyApi, boolean redirectSpotify) throws IOException {
        List<Playable> playables;

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException("'" + url + "' is not a valid URL");
        }
        if (uri.getHost().contains("youtube.com")) {
            Map<String, String> parameterMap = getParameterMap(uri);
            String videoId = parameterMap.get("v");
            String playlistId = parameterMap.get("list");
            if (videoId != null) {
                YouTubeVideo youTubeVideo = youTubeService.requireVideoForId(videoId);
                playables = Lists.newArrayList(youTubeVideo);
            } else if (playlistId != null) {
                YouTubePlaylist youTubePlaylist = youTubeService.playlistForId(playlistId);
                playables = createPlayables(youTubePlaylist);
            } else {
                throw new InvalidCommandException("Detected YouTube URL but no video or playlist id provided.");
            }
        } else if (uri.getHost().equals("youtu.be")) {
            String[] parts = uri.getPath().split("/");
            YouTubeVideo youTubeVideo = youTubeService.requireVideoForId(parts[parts.length - 1]);
            playables = Lists.newArrayList(youTubeVideo);
        } else if (uri.getHost().equals("open.spotify.com")) {
            playables = createPlayablesFromSpotifyUrl(uri, spotifyApi, redirectSpotify);
        } else {
            playables = createPlayablesFromUrl(uri.toString());
        }

        return playables;
    }

    private List<Playable> createPlayablesFromUrl(String url) {
        List<Playable> playables;
        AudioItem audioItem = audioTrackLoader.loadByIdentifier(url);

        if (audioItem == null) {
            throw new NoResultsFoundException("Could not load audio for provided URL.");
        }

        if (audioItem instanceof AudioTrack) {
            playables = Lists.newArrayList(new UrlPlayable((AudioTrack) audioItem));
        } else if (audioItem instanceof AudioPlaylist) {
            AudioPlaylist playlist = (AudioPlaylist) audioItem;
            playables = Lists.newArrayList();
            for (AudioTrack track : playlist.getTracks()) {
                playables.add(new UrlPlayable(track));
            }
        } else {
            throw new UnsupportedOperationException("Expected an AudioTrack or AudioPlaylist but got " + audioItem.getClass().getSimpleName());
        }

        return playables;
    }

    private List<Playable> createPlayablesFromSpotifyUrl(URI uri, SpotifyApi spotifyApi, boolean redirectSpotify) {
        StringList pathFragments = StringList.createWithRegex(uri.getPath(), "/");
        SpotifyService spotifyService = new SpotifyService(spotifyApi);
        if (pathFragments.contains("playlist")) {
            return createPlayableForSpotifyUrlType(pathFragments, "playlist", playlistId -> {
                List<SpotifyTrack> playlistTracks = spotifyService.getPlaylistTracks(playlistId);
                return createPlayables(redirectSpotify, playlistTracks);
            }, spotifyApi);
        } else if (pathFragments.contains("track")) {
            return createPlayableForSpotifyUrlType(pathFragments, "track", trackId -> {
                Track track = spotifyApi.getTrack(trackId).build().execute();
                return Lists.newArrayList(createPlayable(redirectSpotify, track));
            }, spotifyApi);
        } else if (pathFragments.contains("episode")) {
            return createPlayableForSpotifyUrlType(pathFragments, "episode", episodeId -> {
                Episode episode = spotifyApi.getEpisode(episodeId).build().execute();
                return Lists.newArrayList(createPlayable(redirectSpotify, episode));
            }, spotifyApi);
        } else if (pathFragments.contains("album")) {
            return createPlayableForSpotifyUrlType(pathFragments, "album", albumId -> {
                List<Track> albumTracks = spotifyService.getAlbumTracks(albumId);
                return createPlayables(redirectSpotify, albumTracks);
            }, spotifyApi);
        } else if (pathFragments.contains("show")) {
            return createPlayableForSpotifyUrlType(pathFragments, "show", showId -> {
                List<Episode> showEpisodes = spotifyService.getShowEpisodes(showId);
                return createPlayables(redirectSpotify, showEpisodes);
            }, spotifyApi);
        } else {
            throw new InvalidCommandException("Detected Spotify URL but no track, playlist or album id provided.");
        }
    }

    private List<Playable> createPlayableForSpotifyUrlType(StringList pathFragments, String type, CheckedFunction<String, List<Playable>> loadFunc, SpotifyApi spotifyApi) {
        String id = pathFragments.tryGet(pathFragments.indexOf(type) + 1);
        if (Strings.isNullOrEmpty(id)) {
            throw new InvalidCommandException(String.format("No %s id provided", type));
        }

        try {
            String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
            spotifyApi.setAccessToken(accessToken);
            return loadFunc.doApply(id);
        } catch (NotFoundException e) {
            throw new NoResultsFoundException(String.format("No Spotify track found for id '%s'", id));
        } catch (Exception e) {
            throw new RuntimeException("Exception during Spotify request", e);
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

    private Map<String, String> getParameterMap(URI uri) {
        List<NameValuePair> parameters = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        return parameters.stream()
            .filter(param -> param.getName() != null && param.getValue() != null)
            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    }

}

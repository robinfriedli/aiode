package net.robinfriedli.botify.audio;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.exceptions.detailed.NotFoundException;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.spotify.TrackWrapper;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.concurrent.GuildTrackLoadingExecutor;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

/**
 * Factory class for {@link Playable}. Instantiates the matching Playable implementation for given Object or URL
 * and handles populating YouTube playlists and redirecting Spotify tracks asynchronously
 */
public class PlayableFactory {

    private final UrlAudioLoader urlAudioLoader;
    private final YouTubeService youTubeService;
    private final GuildTrackLoadingExecutor trackLoadingExecutor;

    public PlayableFactory(UrlAudioLoader urlAudioLoader, YouTubeService youTubeService, GuildTrackLoadingExecutor trackLoadingExecutor) {
        this.urlAudioLoader = urlAudioLoader;
        this.youTubeService = youTubeService;
        this.trackLoadingExecutor = trackLoadingExecutor;
    }

    /**
     * Creates a single playable for given Object, YouTube video or Spotify track, and redirects the Spotify track if
     * necessary.
     *
     * @param redirectSpotify if true the matching YouTube video is loaded to play the full track using
     * {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}, else a {@link TrackWrapper} is created to play the
     * preview mp3 provided by Spotify
     */
    public Playable createPlayable(boolean redirectSpotify, Object track) {
        if (track instanceof Playable) {
            return (Playable) track;
        } else if (track instanceof Track) {
            if (redirectSpotify) {
                HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) track);
                youTubeService.redirectSpotify(youTubeVideo);
                return youTubeVideo;
            } else {
                return new TrackWrapper((Track) track);
            }
        } else if (track instanceof UrlTrack) {
            return ((UrlTrack) track).asPlayable();
        } else if (track != null) {
            throw new UnsupportedOperationException("Unsupported playable " + track.getClass());
        } else {
            throw new IllegalArgumentException("Cannot create Playable for null");
        }
    }

    public List<Playable> createPlayables(boolean redirectSpotify, Collection<?> tracks) {
        return createPlayables(redirectSpotify, tracks, true);
    }

    /**
     * Creates Playables for a Collection of Objects; YouTube videos or Spotify Tracks.
     *
     * @param redirectSpotify f true the matching YouTube video is loaded to play the full track using
     * {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}, else a {@link TrackWrapper} is created to play the
     * preview mp3 provided by Spotify
     * @param tracks the objects to create a Playable for
     * @param mayInterrupt determines whether loading the playables should be interrupted if the same
     * {@link GuildTrackLoadingExecutor} loads different playables where mayInterrupt is also true. This is used for
     * the play command so that when the user starts playing different tracks the bot can stop wasting resources on loading
     * the playables that won't be needed anymore. This should never be true for command like the Queue or AddCommand
     * where invoking the command again should not cancel out the last command.
     * @return the create Playables
     */
    public List<Playable> createPlayables(boolean redirectSpotify, Collection<?> tracks, boolean mayInterrupt) {
        List<Playable> playables = Lists.newArrayList();
        List<HollowYouTubeVideo> tracksToRedirect = Lists.newArrayList();

        for (Object track : tracks) {
            if (track instanceof Playable) {
                playables.add((Playable) track);
            } else if (track instanceof Track) {
                if (redirectSpotify) {
                    HollowYouTubeVideo youTubeVideo = new HollowYouTubeVideo(youTubeService, (Track) track);
                    tracksToRedirect.add(youTubeVideo);
                    playables.add(youTubeVideo);
                } else {
                    playables.add(new TrackWrapper((Track) track));
                }
            } else if (track instanceof UrlTrack) {
                playables.add(((UrlTrack) track).asPlayable());
            } else if (track != null) {
                throw new UnsupportedOperationException("Unsupported playable " + track.getClass());
            }
        }

        if (!tracksToRedirect.isEmpty()) {
            trackLoadingExecutor.load(() -> {
                for (HollowYouTubeVideo youTubeVideo : tracksToRedirect) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracksToRedirect.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
                        break;
                    }
                    youTubeService.redirectSpotify(youTubeVideo);
                }
            }, mayInterrupt);
        }

        return playables;
    }

    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist) {
        return createPlayables(youTubePlaylist, true);
    }

    /**
     * Populates a YouTube playlist by fetching the data for each {@link HollowYouTubeVideo} asynchronously and returning
     * them as Playables
     *
     * @param youTubePlaylist the YouTube playlist to load the videos for
     * @param mayInterrupt whether or not loading the tracks can be interrupted by a similar action,
     * see {@link #createPlayables(boolean, Collection, boolean)}
     * @return the {@link HollowYouTubeVideo}s as Playables
     */
    public List<Playable> createPlayables(YouTubePlaylist youTubePlaylist, boolean mayInterrupt) {
        List<Playable> playables = Lists.newArrayList(youTubePlaylist.getVideos());

        trackLoadingExecutor.load(() -> youTubeService.populateList(youTubePlaylist), mayInterrupt);

        return playables;
    }

    public List<Playable> createPlayables(String url, SpotifyApi spotifyApi, boolean redirectSpotify) {
        return createPlayables(url, spotifyApi, redirectSpotify, true);
    }

    /**
     * Create Playables for any url. If the url is either an open.spotify or YouTube URL this extracts the ID
     * and uses the familiar methods to load the Playables, otherwise this method uses the {@link UrlAudioLoader}
     * to load the {@link AudioTrack}s using lavaplayer and wraps them in {@link UrlPlayable}s
     */
    public Playable createPlayable(String url, SpotifyApi spotifyApi, boolean redirectSpotify) {
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
                return youTubeService.videoForId(videoId);
            } else {
                throw new IllegalArgumentException("Detected YouTube URL but no video id provided");
            }
        } else if (uri.getHost().equals("youtu.be")) {
            String[] parts = uri.getPath().split("/");
            return youTubeService.videoForId(parts[parts.length - 1]);
        } else if (uri.getHost().equals("open.spotify.com")) {
            StringList pathFragments = StringListImpl.create(uri.getPath(), "/");
            if (pathFragments.contains("track")) {
                String trackId = pathFragments.tryGet(pathFragments.indexOf("track") + 1);
                if (trackId == null) {
                    throw new InvalidCommandException("No track id provided");
                }

                try {
                    String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                    spotifyApi.setAccessToken(accessToken);
                    Track track = spotifyApi.getTrack(trackId).build().execute();
                    return createPlayable(redirectSpotify, track);
                } catch (IOException | SpotifyWebApiException e) {
                    throw new RuntimeException("Exception during Spotify request", e);
                } finally {
                    spotifyApi.setAccessToken(null);
                }
            } else {
                throw new IllegalArgumentException("Detected Spotify URL but no track id provided");
            }
        } else {
            Object o = urlAudioLoader.loadUrl(uri.toString());
            if (o instanceof AudioTrack) {
                return new UrlPlayable((AudioTrack) o);
            } else if (o != null) {
                throw new IllegalArgumentException("Loading provided url did not result in an AudioTrack but " + o.getClass().toString());
            } else {
                throw new IllegalStateException("Loading provided url yielded no results");
            }
        }
    }

    public List<Playable> createPlayables(String url, SpotifyApi spotifyApi, boolean redirectSpotify, boolean mayInterrupt) {
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
                YouTubeVideo youTubeVideo = youTubeService.videoForId(videoId);
                playables = Lists.newArrayList(youTubeVideo);
            } else if (playlistId != null) {
                YouTubePlaylist youTubePlaylist = youTubeService.playlistForId(playlistId);
                playables = createPlayables(youTubePlaylist, mayInterrupt);
            } else {
                throw new InvalidCommandException("Detected YouTube URL but no video or playlist id provided.");
            }
        } else if (uri.getHost().equals("youtu.be")) {
            String[] parts = uri.getPath().split("/");
            YouTubeVideo youTubeVideo = youTubeService.videoForId(parts[parts.length - 1]);
            playables = Lists.newArrayList(youTubeVideo);
        } else if (uri.getHost().equals("open.spotify.com")) {
            playables = createPlayablesFromSpotifyUrl(uri, spotifyApi, redirectSpotify, mayInterrupt);
        } else {
            playables = createPlayablesFromUrl(uri.toString());
        }

        return playables;
    }

    private List<Playable> createPlayablesFromUrl(String url) {
        List<Playable> playables;
        Object result = urlAudioLoader.loadUrl(url);

        if (result == null || result instanceof Throwable) {
            String errorMessage = "Could not load audio for provided URL.";

            if (result != null) {
                errorMessage = errorMessage + " " + ((Throwable) result).getMessage();
            }

            throw new NoResultsFoundException(errorMessage);
        }

        if (result instanceof AudioTrack) {
            playables = Lists.newArrayList(new UrlPlayable((AudioTrack) result));
        } else if (result instanceof AudioPlaylist) {
            AudioPlaylist playlist = (AudioPlaylist) result;
            playables = Lists.newArrayList();
            for (AudioTrack track : playlist.getTracks()) {
                playables.add(new UrlPlayable(track));
            }
        } else {
            throw new UnsupportedOperationException("Expected an AudioTrack or AudioPlaylist but got " + result.getClass().getSimpleName());
        }

        return playables;
    }

    private List<Playable> createPlayablesFromSpotifyUrl(URI uri, SpotifyApi spotifyApi, boolean redirectSpotify, boolean mayInterrupt) {
        StringList pathFragments = StringListImpl.create(uri.getPath(), "/");
        SpotifyService spotifyService = new SpotifyService(spotifyApi);
        if (pathFragments.contains("playlist")) {
            String playlistId = pathFragments.tryGet(pathFragments.indexOf("playlist") + 1);
            if (playlistId == null) {
                throw new InvalidCommandException("No playlist id provided");
            }

            try {
                String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);
                List<Track> playlistTracks = spotifyService.getPlaylistTracks(playlistId);
                return createPlayables(redirectSpotify, playlistTracks, mayInterrupt);
            } catch (NotFoundException e) {
                throw new NoResultsFoundException("No playlist found for id " + playlistId);
            } catch (IOException | SpotifyWebApiException e) {
                throw new RuntimeException("Exception during Spotify request", e);
            } finally {
                spotifyApi.setAccessToken(null);
            }
        } else if (pathFragments.contains("track")) {
            String trackId = pathFragments.tryGet(pathFragments.indexOf("track") + 1);
            if (trackId == null) {
                throw new InvalidCommandException("No track id provided");
            }

            try {
                String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);
                Track track = spotifyApi.getTrack(trackId).build().execute();
                return Lists.newArrayList(createPlayable(redirectSpotify, track));
            } catch (NotFoundException e) {
                throw new NoResultsFoundException("No track found for id " + trackId);
            } catch (IOException | SpotifyWebApiException e) {
                throw new RuntimeException("Exception during Spotify request", e);
            } finally {
                spotifyApi.setAccessToken(null);
            }
        } else if (pathFragments.contains("album")) {
            String albumId = pathFragments.tryGet(pathFragments.indexOf("album") + 1);
            if (albumId == null) {
                throw new InvalidCommandException("No album id provided");
            }

            try {
                String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);
                List<Track> albumTracks = spotifyService.getAlbumTracks(albumId);
                return createPlayables(redirectSpotify, albumTracks, mayInterrupt);
            } catch (BadRequestException e) {
                throw new NoResultsFoundException("No album found for id " + albumId);
            } catch (IOException | SpotifyWebApiException e) {
                throw new RuntimeException("Exception during Spotify request", e);
            } finally {
                spotifyApi.setAccessToken(null);
            }
        } else {
            throw new InvalidCommandException("Detected Spotify URL but no track, playlist or album id provided.");
        }
    }

    private Map<String, String> getParameterMap(URI uri) {
        List<NameValuePair> parameters = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        Map<String, String> parameterMap = new HashMap<>();
        parameters.forEach(param -> parameterMap.put(param.getName(), param.getValue()));
        return parameterMap;
    }

}

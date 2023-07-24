package net.robinfriedli.aiode.audio.spotify;

import java.util.regex.Pattern;

import net.robinfriedli.aiode.audio.playables.PlayableContainer;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.playables.containers.EpisodePlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyAlbumPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyPlaylistPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.SpotifyShowPlayableContainer;
import net.robinfriedli.aiode.audio.playables.containers.TrackPlayableContainer;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.function.SpotifyInvoker;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.Show;
import se.michaelthelin.spotify.model_objects.specification.Track;

/**
 * class to recognise, parse and load Spotify URIs. Currently Spotify URIs that point directly to a track, playlist or
 * album are supported, e.g. spotify:track:697M5JB8FDIyRXEXgl1pBZ
 */
public class SpotifyUri {

    private static final Pattern URI_REGEX = Pattern.compile("spotify:(track|album|playlist|episode|show):[a-zA-Z0-9]{22}");

    private final String id;
    private final Type type;

    public SpotifyUri(String uri) {
        if (Type.TRACK.getPattern().matcher(uri).matches()) {
            type = Type.TRACK;
        } else if (Type.ALBUM.getPattern().matcher(uri).matches()) {
            type = Type.ALBUM;
        } else if (Type.PLAYLIST.getPattern().matcher(uri).matches()) {
            type = Type.PLAYLIST;
        } else if (Type.EPISODE.getPattern().matcher(uri).matches()) {
            type = Type.EPISODE;
        } else if (Type.SHOW.getPattern().matcher(uri).matches()) {
            type = Type.SHOW;
        } else {
            throw new InvalidCommandException("Unsupported URI! Supported: spotify:track, spotify:album, spotify:playlist, spotify:episode, spotify:show");
        }
        id = parseId(uri);
    }

    public static SpotifyUri parse(String uri) {
        return new SpotifyUri(uri);
    }

    public static boolean isSpotifyUri(String s) {
        return URI_REGEX.matcher(s).matches();
    }

    private static String parseId(String s) {
        String[] split = s.split(":");
        return split[split.length - 1];
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public PlayableContainer<?> createPlayableContainer(
        PlayableFactory playableFactory,
        SpotifyService spotifyService
    ) throws Exception {
        return type.createPlayableContainer(playableFactory, spotifyService, this);
    }

    public enum Type {

        TRACK(Pattern.compile("spotify:track:[a-zA-Z0-9]{22}")) {
            @Override
            public PlayableContainer<?> createPlayableContainer(
                PlayableFactory playableFactory,
                SpotifyService spotifyService,
                SpotifyUri uri
            ) throws Exception {
                SpotifyInvoker invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
                Track track;
                try {
                    track = invoker.invoke(() -> spotifyService.getTrack(uri.getId()));
                } catch (NotFoundException e) {
                    throw new InvalidCommandException("No results found for id " + uri.getId());
                }
                return new TrackPlayableContainer(track);
            }
        },
        ALBUM(Pattern.compile("spotify:album:[a-zA-Z0-9]{22}")) {
            @Override
            public PlayableContainer<?> createPlayableContainer(
                PlayableFactory playableFactory,
                SpotifyService spotifyService,
                SpotifyUri uri
            ) throws Exception {
                SpotifyInvoker invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
                Album album;
                try {
                    album = invoker.invoke(() -> spotifyService.getAlbum(uri.getId()));
                } catch (NotFoundException e) {
                    throw new InvalidCommandException("No results found for id " + uri.getId());
                }
                return new SpotifyAlbumPlayableContainer(album);
            }
        },
        PLAYLIST(Pattern.compile("spotify:playlist:[a-zA-Z0-9]{22}")) {
            @Override
            public PlayableContainer<?> createPlayableContainer(
                PlayableFactory playableFactory,
                SpotifyService spotifyService,
                SpotifyUri uri
            ) throws Exception {
                SpotifyInvoker invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
                Playlist playlist;
                try {
                    playlist = invoker.invoke(() -> spotifyService.getPlaylist(uri.getId()));
                } catch (NotFoundException e) {
                    throw new InvalidCommandException("No results found for id " + uri.getId());
                }
                return new SpotifyPlaylistPlayableContainer(playlist);
            }
        },
        EPISODE(Pattern.compile("spotify:episode:[a-zA-Z0-9]{22}")) {
            @Override
            public PlayableContainer<?> createPlayableContainer(
                PlayableFactory playableFactory,
                SpotifyService spotifyService,
                SpotifyUri uri
            ) throws Exception {
                SpotifyInvoker invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
                Episode episode;
                try {
                    episode = invoker.invoke(() -> spotifyService.getEpisode(uri.getId()));
                } catch (NotFoundException e) {
                    throw new InvalidCommandException("No results found for id " + uri.getId());
                }
                return new EpisodePlayableContainer(episode);
            }
        },
        SHOW(Pattern.compile("spotify:show:[a-zA-Z0-9]{22}")) {
            @Override
            public PlayableContainer<?> createPlayableContainer(
                PlayableFactory playableFactory,
                SpotifyService spotifyService,
                SpotifyUri uri
            ) throws Exception {
                SpotifyInvoker invoker = SpotifyInvoker.create(spotifyService.getSpotifyApi());
                Show show;
                try {
                    show = invoker.invoke(() -> spotifyService.getShow(uri.getId()));
                } catch (NotFoundException e) {
                    throw new InvalidCommandException("No results found for id " + uri.getId());
                }
                return new SpotifyShowPlayableContainer(show);
            }
        };

        private final Pattern pattern;

        Type(Pattern pattern) {
            this.pattern = pattern;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public abstract PlayableContainer<?> createPlayableContainer(
            PlayableFactory playableFactory,
            SpotifyService spotifyService,
            SpotifyUri uri
        ) throws Exception;

    }

}

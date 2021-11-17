package net.robinfriedli.aiode.audio.spotify;

import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import net.robinfriedli.stringlist.StringList;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.ExternalUrl;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

/**
 * Wrap any Spotify entity that implements {@link IPlaylistItem} and provide access to their common methods. This is required
 * because Spotify does not provide these methods on the interface due to terrible API design.
 * See related <a href="https://github.com/thelinmichael/spotify-web-api-java/issues/213">GitHub issue</a>.
 */
public class SpotifyTrack {

    private final IPlaylistItem wrapped;

    public SpotifyTrack(IPlaylistItem wrapped) {
        this.wrapped = wrapped;
    }

    public static SpotifyTrack wrap(IPlaylistItem playlistItem) {
        return new SpotifyTrack(playlistItem);
    }

    @Nullable
    public static SpotifyTrack wrapIfNotNull(@Nullable IPlaylistItem playlistItem) {
        if (playlistItem != null) {
            return wrap(playlistItem);
        }

        return null;
    }

    /**
     * Enforce all cases to be covered at compile time by providing a function for each case.
     * This function will need to be updated if Spotify adds additional {@link IPlaylistItem} implementations in the
     * future which will force all usages of this method to be updated as well.
     */
    public <R> R exhaustiveMatch(Function<Track, R> trackFunction, Function<Episode, R> episodeFunction) {
        if (isInstance(Track.class)) {
            return trackFunction.apply(cast(Track.class));
        } else if (isInstance(Episode.class)) {
            return episodeFunction.apply(cast(Episode.class));
        }

        throw new UnsupportedOperationException("Unsupported PlaylistItem type: " + wrapped.getClass());
    }

    public boolean isInstance(Class<? extends IPlaylistItem> type) {
        return type.isInstance(wrapped);
    }

    @Nullable
    public <E extends IPlaylistItem> E tryCast(Class<E> type) {
        if (isInstance(type)) {
            return type.cast(wrapped);
        }
        return null;
    }

    public <E extends IPlaylistItem> E cast(Class<E> type) {
        return type.cast(wrapped);
    }

    public String getPreviewUrl() {
        return exhaustiveMatch(
            Track::getPreviewUrl,
            Episode::getAudioPreviewUrl
        );
    }

    public Integer getDurationMs() {
        return exhaustiveMatch(
            Track::getDurationMs,
            Episode::getDurationMs
        );
    }

    public Boolean getIsExplicit() {
        return exhaustiveMatch(
            Track::getIsExplicit,
            Episode::getExplicit
        );
    }

    public ExternalUrl getExternalUrls() {
        return exhaustiveMatch(
            Track::getExternalUrls,
            Episode::getExternalUrls
        );
    }

    public String getHref() {
        return exhaustiveMatch(
            Track::getHref,
            Episode::getHref
        );
    }

    public String getId() {
        return exhaustiveMatch(
            Track::getId,
            Episode::getId
        );
    }

    public Boolean getIsPlayable() {
        return exhaustiveMatch(
            Track::getIsPlayable,
            Episode::getPlayable
        );
    }

    public String getName() {
        return exhaustiveMatch(
            Track::getName,
            Episode::getName
        );
    }

    public String getUri() {
        return exhaustiveMatch(
            Track::getUri,
            Episode::getUri
        );
    }

    public String getAlbumCoverUrl() {
        return exhaustiveMatch(
            track -> {
                AlbumSimplified album = track.getAlbum();
                if (album != null) {
                    Image[] images = album.getImages();
                    if (images != null && images.length > 0) {
                        return images[0].getUrl();
                    }
                }

                return null;
            },
            episode -> {
                Image[] images = episode.getImages();
                if (images != null && images.length > 0) {
                    return images[0].getUrl();
                }

                return null;
            }
        );
    }

    public String getDisplay() {
        String name = getName();
        String artistString = exhaustiveMatch(
            track -> StringList.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", "),
            episode -> {
                ShowSimplified show = episode.getShow();
                if (show != null) {
                    return show.getName();
                }

                return null;
            }
        );

        if (!Strings.isNullOrEmpty(artistString)) {
            return String.format("%s by %s", name, artistString);
        } else {
            return name;
        }
    }

    public SpotifyTrackKind getKind() {
        return exhaustiveMatch(
            track -> SpotifyTrackKind.TRACK,
            episode -> SpotifyTrackKind.EPISODE
        );
    }

}

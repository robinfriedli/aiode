package net.robinfriedli.botify.audio.spotify;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import net.robinfriedli.botify.function.SpotifyInvoker;

import static net.robinfriedli.botify.util.SearchEngine.*;

/**
 * Service to request data from the Spotify API. Be sure to call these methods with the appropriate Spotify credentials,
 * see {@link SpotifyInvoker}. If you use these methods in a command you can use the runWithLogin or runWithCredentials method
 * of the AbstractCommand
 */
public class SpotifyService {

    private final SpotifyApi spotifyApi;

    public SpotifyService(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public Track getTrack(String id) throws IOException, SpotifyWebApiException {
        return spotifyApi.getTrack(id).build().execute();
    }

    public Artist getArtist(String id) throws IOException, SpotifyWebApiException {
        return spotifyApi.getArtist(id).build().execute();
    }

    public List<Track> searchTrack(String searchTerm) throws IOException, SpotifyWebApiException {
        return searchTrack(searchTerm, false);
    }

    public List<Track> searchTrack(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException {
        return searchTrack(searchTerm, limitToLibrary, 20);
    }

    public List<Track> searchTrack(String searchTerm, boolean limitToLibrary, int limit) throws IOException, SpotifyWebApiException {
        List<Track> trackList = Lists.newArrayList();
        if (limitToLibrary) {
            String next;
            int i = 0;
            do {
                Paging<Track> paging = spotifyApi.searchTracks(searchTerm).limit(50).offset(i * 50).build().execute();
                next = paging.getNext();
                Track[] tracks = paging.getItems();

                if (tracks.length == 0) {
                    break;
                }

                String[] ids = Arrays.stream(tracks).filter(t -> t != null && t.getId() != null).map(Track::getId).toArray(String[]::new);
                Boolean[] trackIsInLibraryArray = spotifyApi.checkUsersSavedTracks(ids).build().execute();
                for (int j = 0; j < tracks.length; j++) {
                    boolean trackIsInLibrary = trackIsInLibraryArray[j];
                    if (trackIsInLibrary) {
                        if (trackList.size() < limit) {
                            trackList.add(tracks[j]);
                        } else {
                            return trackList;
                        }
                    }
                }

                i++;
            } while (next != null && trackList.size() < limit && i < 10);
        } else {
            Track[] tracks = spotifyApi.searchTracks(searchTerm).limit(limit).build().execute().getItems();
            trackList.addAll(Arrays.asList(tracks));
        }

        return trackList;
    }

    public Playlist getPlaylist(String id) throws IOException, SpotifyWebApiException {
        return spotifyApi.getPlaylist(id).build().execute();
    }

    public List<PlaylistSimplified> searchPlaylist(String searchTerm) throws IOException, SpotifyWebApiException {
        PlaylistSimplified[] results = spotifyApi.searchPlaylists(searchTerm).build().execute().getItems();
        return getBestLevenshteinMatches(results, searchTerm.toLowerCase(), playlist -> playlist.getName().toLowerCase());
    }

    public List<PlaylistSimplified> searchOwnPlaylist(String searchTerm) throws IOException, SpotifyWebApiException {
        List<PlaylistSimplified> playlists = Lists.newArrayList();
        int limit = 50;
        int offset = 0;
        String nextPage;
        do {
            Paging<PlaylistSimplified> paging = spotifyApi.getListOfCurrentUsersPlaylists().offset(offset).limit(limit).build().execute();
            playlists.addAll(Arrays.asList(paging.getItems()));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return getBestLevenshteinMatches(playlists, searchTerm, PlaylistSimplified::getName);
    }

    public List<Track> getPlaylistTracks(String playlistId) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        int limit = 100;
        int offset = 0;
        String nextPage;
        do {
            Paging<PlaylistTrack> paging = spotifyApi.getPlaylistsTracks(playlistId).offset(offset).limit(limit).build().execute();
            PlaylistTrack[] items = paging.getItems();
            tracks.addAll(Arrays.stream(items).map(PlaylistTrack::getTrack).filter(Objects::nonNull).collect(Collectors.toList()));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return tracks;
    }

    public List<Track> getPlaylistTracks(PlaylistSimplified playlistSimplified) throws IOException, SpotifyWebApiException {
        return getPlaylistTracks(playlistSimplified.getId());
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm) throws IOException, SpotifyWebApiException {
        return searchAlbum(searchTerm, false);
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException {
        List<AlbumSimplified> albumList = Lists.newArrayList();
        if (limitToLibrary) {
            String next;
            int i = 0;
            do {
                Paging<AlbumSimplified> paging = spotifyApi.searchAlbums(searchTerm).limit(50).offset(i * 50).build().execute();
                next = paging.getNext();
                AlbumSimplified[] albums = paging.getItems();

                if (albums.length == 0) {
                    break;
                }

                String[] ids = Arrays.stream(albums).map(AlbumSimplified::getId).toArray(String[]::new);
                Boolean[] albumIsInLibraryArray = spotifyApi.checkUsersSavedAlbums(ids).build().execute();
                for (int j = 0; j < albums.length; j++) {
                    boolean trackIsInLibrary = albumIsInLibraryArray[j];
                    if (trackIsInLibrary) {
                        albumList.add(albums[j]);
                    }
                }

                i++;
            } while (next != null && i < 10);
        } else {
            AlbumSimplified[] albums = spotifyApi.searchAlbums(searchTerm).build().execute().getItems();
            albumList.addAll(Arrays.asList(albums));
        }

        return albumList;
    }

    public List<Track> getAlbumTracks(String albumId) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        int limit = 50;
        int offset = 0;
        String nextPage;
        do {
            Paging<TrackSimplified> paging = spotifyApi.getAlbumsTracks(albumId).offset(offset).limit(limit).build().execute();
            TrackSimplified[] items = paging.getItems();
            Track[] albumTracks = spotifyApi
                .getSeveralTracks(Arrays.stream(items).filter(Objects::nonNull).map(TrackSimplified::getId).toArray(String[]::new))
                .build()
                .execute();
            tracks.addAll(Arrays.asList(albumTracks));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return tracks;
    }

    public List<Track> getAlbumTracks(AlbumSimplified albumSimplified) throws IOException, SpotifyWebApiException {
        return getAlbumTracks(albumSimplified.getId());
    }

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

}

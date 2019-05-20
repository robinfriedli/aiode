package net.robinfriedli.botify.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.text.similarity.LevenshteinDistance;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.SavedTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.jxp.api.XmlElement;
import org.hibernate.Session;

/**
 * class with static methods to search spotify or local tracks / playlists. For YouTube searches see {@link YouTubeService}
 */
public class SearchEngine {

    private static final int MAX_LEVENSHTEIN_DISTANCE = 4;

    public static List<Track> searchTrack(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        Track[] tracks = spotifyApi.searchTracks(searchTerm).market(CountryCode.CH).build().execute().getItems();
        return Lists.newArrayList(tracks);
    }

    public static List<Track> searchOwnTrack(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        int limit = 50;
        int offset = 0;
        String nextPage;
        do {
            Paging<SavedTrack> paging = spotifyApi.getUsersSavedTracks().offset(offset).limit(limit).build().execute();
            SavedTrack[] part = paging.getItems();
            tracks.addAll(Arrays.stream(part).map(SavedTrack::getTrack).collect(Collectors.toList()));
            nextPage = paging.getNext();
            offset = offset + limit;
        } while (nextPage != null);
        return getBestLevenshteinMatches(tracks, searchTerm.toLowerCase(), track -> track.getName().toLowerCase());
    }

    @Nullable
    public static Playlist searchLocalList(Session session, String searchTerm, boolean isPartitioned, String guildId) {
        Optional<Playlist> playlist;
        String baseQuery = "from " + Playlist.class.getName() + " where lower(name) like lower('" + searchTerm.replaceAll("'", "''") + "')";
        if (isPartitioned) {
            playlist = session
                .createQuery(baseQuery + " and guild_id = '" + guildId + "'", Playlist.class)
                .uniqueResultOptional();
        } else {
            playlist = session.createQuery(baseQuery, Playlist.class).uniqueResultOptional();
        }

        return playlist.orElse(null);
    }

    public static List<PlaylistItem> searchPlaylistItems(Playlist playlist, String searchTerm) {
        return playlist.getPlaylistItems().stream().filter(item -> item.matches(searchTerm)).collect(Collectors.toList());
    }

    public static List<PlaylistSimplified> searchSpotifyPlaylist(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        PlaylistSimplified[] results = spotifyApi.searchPlaylists(searchTerm).build().execute().getItems();
        return getBestLevenshteinMatches(results, searchTerm.toLowerCase(), playlist -> playlist.getName().toLowerCase());
    }

    public static List<AlbumSimplified> searchSpotifyAlbum(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        AlbumSimplified[] albums = spotifyApi.searchAlbums(searchTerm).build().execute().getItems();
        return getBestLevenshteinMatches(albums, searchTerm.toLowerCase(), album -> album.getName().toLowerCase());
    }

    public static List<Track> getPlaylistTracks(SpotifyApi spotifyApi, String playlistId) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        int limit = 100;
        int offset = 0;
        String nextPage;
        do {
            Paging<PlaylistTrack> paging = spotifyApi.getPlaylistsTracks(playlistId).offset(offset).limit(limit).build().execute();
            PlaylistTrack[] items = paging.getItems();
            tracks.addAll(Arrays.stream(items).map(PlaylistTrack::getTrack).collect(Collectors.toList()));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return tracks;
    }

    public static List<Track> getAlbumTracks(SpotifyApi spotifyApi, String albumId) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        int limit = 50;
        int offset = 0;
        String nextPage;
        do {
            Paging<TrackSimplified> paging = spotifyApi.getAlbumsTracks(albumId).offset(offset).limit(limit).build().execute();
            TrackSimplified[] items = paging.getItems();
            Track[] albumTracks = spotifyApi
                .getSeveralTracks(Arrays.stream(items).map(TrackSimplified::getId).toArray(String[]::new))
                .build()
                .execute();
            tracks.addAll(Arrays.asList(albumTracks));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return tracks;
    }

    public static List<Track> getPlaylistTracks(SpotifyApi spotifyApi, PlaylistSimplified playlistSimplified) throws IOException, SpotifyWebApiException {
        return getPlaylistTracks(spotifyApi, playlistSimplified.getId());
    }

    public static List<PlaylistSimplified> searchOwnPlaylist(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
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

    public static Predicate<XmlElement> editDistanceAttributeCondition(String attribute, String searchTerm) {
        return xmlElement -> {
            LevenshteinDistance editDistance = LevenshteinDistance.getDefaultInstance();
            return xmlElement.hasAttribute(attribute)
                && editDistance.apply(xmlElement.getAttribute(attribute).getValue().toLowerCase(), searchTerm.toLowerCase()) < MAX_LEVENSHTEIN_DISTANCE;
        };
    }

    @Nullable
    public static Preset searchPreset(Session session, String name, String guildId) {
        Optional<Preset> optionalPreset = session
            .createQuery("from " + Preset.class.getName() + " where guild_id = '" + guildId + "' and name = '" + name.replaceAll("'", "''") + "'", Preset.class)
            .uniqueResultOptional();

        return optionalPreset.orElse(null);
    }

    private static <E> List<E> getBestLevenshteinMatches(E[] objects, String searchTerm, Function<E, String> compareFunc) {
        return getBestLevenshteinMatches(Arrays.asList(objects), searchTerm, compareFunc);
    }

    private static <E> List<E> getBestLevenshteinMatches(List<E> objects, String searchTerm, Function<E, String> compareFunc) {
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        Multimap<Integer, E> objectsWithDistance = HashMultimap.create();

        for (E object : objects) {
            Integer editDistance = levenshteinDistance.apply(searchTerm.toLowerCase(), compareFunc.apply(object).toLowerCase());
            if (editDistance < MAX_LEVENSHTEIN_DISTANCE) {
                objectsWithDistance.put(editDistance, object);
            }
        }

        if (objectsWithDistance.isEmpty()) {
            return Lists.newArrayList();
        } else {
            Integer bestMatch = Collections.min(objectsWithDistance.keySet());
            return Lists.newArrayList(objectsWithDistance.get(bestMatch));
        }
    }

}

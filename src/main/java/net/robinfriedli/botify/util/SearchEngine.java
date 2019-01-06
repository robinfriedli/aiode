package net.robinfriedli.botify.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.SavedTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * class with static methods to search spotify or local tracks / playlists. For YouTube searches see {@link YouTubeService}
 */
public class SearchEngine {

    private static final int MAX_LEVENSHTEIN_DISTANCE = 4;
    private static final int MAX_REQUESTS = 100;

    public static List<Track> searchTrack(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        Track[] tracks = spotifyApi.searchTracks(searchTerm).market(CountryCode.CH).build().execute().getItems();
        if (tracks.length >= 1) {
            LevenshteinDistance editDistance = new LevenshteinDistance();
            Multimap<Integer, Track> tracksWithDistance = HashMultimap.create();
            for (Track track : tracks) {
                Integer distance = editDistance.apply(searchTerm.toLowerCase(), track.getName().toLowerCase());
                tracksWithDistance.put(distance, track);
            }

            Integer bestMatch = Collections.min(tracksWithDistance.keySet());
            return Lists.newArrayList(tracksWithDistance.get(bestMatch));
        }

        return Lists.newArrayList();
    }

    public static List<Track> searchOwnTrack(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        for (int i = 0; i < MAX_REQUESTS; i++) {
            SavedTrack[] part = spotifyApi.getUsersSavedTracks().offset(i * 50).limit(50).build().execute().getItems();
            if (part.length == 0) {
                break;
            }
            tracks.addAll(Arrays.stream(part).map(SavedTrack::getTrack).collect(Collectors.toList()));
            if (part.length < 50) {
                break;
            }
        }
        return getBestLevenshteinMatches(tracks, searchTerm, Track::getName);
    }

    @Nullable
    public static Playlist searchLocalList(Context context, String searchTerm) {
        List<XmlElement> found = context.query(and(instanceOf(Playlist.class), editDistanceAttributeCondition("name", searchTerm))).collect();
        if (found.size() == 1) {
            return (Playlist) found.get(0);
        } else if (found.size() > 1) {
            LevenshteinDistance editDistance = LevenshteinDistance.getDefaultInstance();
            Multimap<Integer, XmlElement> elementsWithEditDistance = HashMultimap.create();
            for (XmlElement xmlElement : found) {
                elementsWithEditDistance.put(editDistance.apply(searchTerm.toLowerCase(), xmlElement.getAttribute("name").getValue().toLowerCase()), xmlElement);
            }

            Integer bestMatch = Collections.min(elementsWithEditDistance.keySet());
            Collection<XmlElement> matches = elementsWithEditDistance.get(bestMatch);
            if (matches.size() == 1) {
                return (Playlist) matches.iterator().next();
            } else {
                throw new IllegalStateException("More than one playlists found for input " + searchTerm);
            }
        }

        return null;
        //return (Playlist) context.query(and(instanceOf(Playlist.class), attribute("name").is(searchTerm))).getOnlyResult();
    }

    public static List<XmlElement> getPlaylistItems(Playlist playlist, String searchTerm) {
        List<XmlElement> found = Query.evaluate(or(
            and(
                instanceOf(Song.class),
                editDistanceAttributeCondition("name", searchTerm)
            ),
            and(
                instanceOf(Video.class),
                or(
                    and(
                        editDistanceAttributeCondition("title", searchTerm),
                        xmlElement -> !xmlElement.hasAttribute("spotifyTrackName")
                    ),
                    and(
                        editDistanceAttributeCondition("spotifyTrackName", searchTerm),
                        xmlElement -> xmlElement.hasAttribute("spotifyTrackName")
                    )
                )
            )
        )).execute(playlist.getSubElements()).collect();

        return getBestLevenshteinMatches(found, searchTerm, element -> {
            if (element instanceof Song) {
                return element.getAttribute("name").getValue();
            } else {
                if (element.hasAttribute("spotifyTrackName")) {
                    return element.getAttribute("spotifyTrackName").getValue();
                } else {
                    return element.getAttribute("title").getValue();
                }
            }
        });
    }

    public static List<PlaylistSimplified> searchSpotifyPlaylist(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        PlaylistSimplified[] results = spotifyApi.searchPlaylists(searchTerm).build().execute().getItems();
        if (results.length >= 1) {
            LevenshteinDistance editDistance = LevenshteinDistance.getDefaultInstance();
            Multimap<Integer, PlaylistSimplified> playlistsWithEditDistance = HashMultimap.create();
            for (PlaylistSimplified playlist : results) {
                Integer distance = editDistance.apply(searchTerm.toLowerCase(), playlist.getName().toLowerCase());
                playlistsWithEditDistance.put(distance, playlist);
            }

            Integer bestMatch = Collections.min(playlistsWithEditDistance.keySet());
            return Lists.newArrayList(playlistsWithEditDistance.get(bestMatch));
        }

        return Lists.newArrayList();
    }

    public static List<Track> getPlaylistTracks(SpotifyApi spotifyApi, PlaylistSimplified playlistSimplified) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        for (int i = 0; i < MAX_REQUESTS; i++) {
            PlaylistTrack[] items = spotifyApi.getPlaylistsTracks(playlistSimplified.getId()).offset(i * 100).limit(100)
                .build().execute().getItems();
            if (items.length == 0) {
                break;
            }
            tracks.addAll(Arrays.stream(items).map(PlaylistTrack::getTrack).collect(Collectors.toList()));
            if (items.length < 100) {
                break;
            }
        }
        return tracks;
    }

    public static List<PlaylistSimplified> searchOwnPlaylist(SpotifyApi spotifyApi, String searchTerm) throws IOException, SpotifyWebApiException {
        PlaylistSimplified[] ownPlaylists = spotifyApi.getListOfCurrentUsersPlaylists().build().execute().getItems();
        return getBestLevenshteinMatches(ownPlaylists, searchTerm, PlaylistSimplified::getName);
    }

    public static Predicate<XmlElement> editDistanceAttributeCondition(String attribute, String searchTerm) {
        return xmlElement -> {
            LevenshteinDistance editDistance = LevenshteinDistance.getDefaultInstance();
            return xmlElement.hasAttribute(attribute)
                && editDistance.apply(xmlElement.getAttribute(attribute).getValue().toLowerCase(), searchTerm.toLowerCase()) < MAX_LEVENSHTEIN_DISTANCE;
        };
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

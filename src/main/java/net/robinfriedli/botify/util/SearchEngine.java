package net.robinfriedli.botify.util;

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
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.persist.qb.builders.EntityQueryBuilder;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.qb.interceptor.interceptors.PartitionedQueryInterceptor;
import net.robinfriedli.jxp.api.XmlElement;
import org.hibernate.Session;

/**
 * class with static methods to search local playlists and other entities. For YouTube searches see {@link YouTubeService},
 * for Spotify see {@link SpotifyService}
 */
public class SearchEngine {

    private static final int MAX_LEVENSHTEIN_DISTANCE = 4;

    @Nullable
    public static Playlist searchLocalList(Session session, String searchTerm) {
        String searchName = Playlist.sanatizeName(searchTerm).replaceAll("'", "''").toLowerCase();
        QueryBuilderFactory queryBuilderFactory = Botify.get().getQueryBuilderFactory();
        return queryBuilderFactory.find(Playlist.class)
            .where((cb, root, query) -> cb.equal(cb.lower(root.get("name")), searchName))
            .build(session)
            .uniqueResultOptional()
            .orElse(null);
    }

    @Nullable
    public static Playlist searchLocalList(Session session, String searchTerm, boolean isPartitioned, String guildId) {
        String searchName = Playlist.sanatizeName(searchTerm).replaceAll("'", "''").toLowerCase();
        QueryBuilderFactory queryBuilderFactory = Botify.get().getQueryBuilderFactory();
        EntityQueryBuilder<Playlist> queryBuilder = queryBuilderFactory
            .find(Playlist.class)
            .where((cb, root, query) -> cb.equal(cb.lower(root.get("name")), searchName));
        if (isPartitioned) {
            queryBuilder.where((cb, root, query) -> cb.equal(root.get("guildId"), guildId));
        }

        return queryBuilder.skipInterceptors(PartitionedQueryInterceptor.class).build(session).uniqueResultOptional().orElse(null);
    }

    public static List<PlaylistItem> searchPlaylistItems(Playlist playlist, String searchTerm) {
        return playlist.getItemsSorted().stream().filter(item -> item.matches(searchTerm)).collect(Collectors.toList());
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

    public static <E> List<E> getBestLevenshteinMatches(E[] objects, String searchTerm, Function<E, String> compareFunc) {
        return getBestLevenshteinMatches(true, objects, searchTerm, compareFunc);
    }

    public static <E> List<E> getBestLevenshteinMatches(boolean limitDistance, E[] objects, String searchTerm, Function<E, String> compareFunc) {
        return getBestLevenshteinMatches(limitDistance, Arrays.asList(objects), searchTerm, compareFunc);
    }

    public static <E> List<E> getBestLevenshteinMatches(List<E> objects, String searchTerm, Function<E, String> compareFunc) {
        return getBestLevenshteinMatches(true, objects, searchTerm, compareFunc);
    }

    public static <E> List<E> getBestLevenshteinMatches(boolean limitDistance, List<E> objects, String searchTerm, Function<E, String> compareFunc) {
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        Multimap<Integer, E> objectsWithDistance = HashMultimap.create();

        for (E object : objects) {
            Integer editDistance = levenshteinDistance.apply(searchTerm.toLowerCase(), compareFunc.apply(object).toLowerCase());
            if (!limitDistance || editDistance < MAX_LEVENSHTEIN_DISTANCE) {
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

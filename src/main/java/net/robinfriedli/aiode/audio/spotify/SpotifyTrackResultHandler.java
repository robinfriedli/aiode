package net.robinfriedli.aiode.audio.spotify;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.LevenshteinDistance;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;
import org.hibernate.query.Query;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

/**
 * Determines the best result from a selection of results based on how popular each artist is on this guild, the edit
 * distance between the found track name to the search term and the popularity of the track
 */
public class SpotifyTrackResultHandler {

    private final Guild guild;
    private final Session session;

    public SpotifyTrackResultHandler(Guild guild, Session session) {
        this.guild = guild;
        this.session = session;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public Track getBestResult(String searchTerm, Collection<Track> tracks) {
        Map<String, Long> playbackCountWithArtistId = getPlaybackCountForArtists(tracks);
        String trackName = extractSearchedTrackName(searchTerm, tracks).toLowerCase();
        LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();

        long bestArtistScore = playbackCountWithArtistId.values().stream().mapToLong(l -> l).max().orElse(0);
        int bestPopularity = tracks.stream().mapToInt(Track::getPopularity).max().orElse(0);
        int maxEditDistance = tracks.stream()
            .mapToInt(t -> levenshteinDistance.apply(trackName, t.getName().toLowerCase())).max().orElse(0);
        // the importance of the artist score should be lower if the best artist was only played a few times
        int maxArtistScore = bestArtistScore > 100 ? 10 : (int) (bestArtistScore * 10 / 100);

        Multimap<Integer, Track> tracksByScore = HashMultimap.create();
        for (Track track : tracks) {
            long maxArtistCount = Arrays.stream(track.getArtists()).mapToLong(a -> {
                Long artistCount = playbackCountWithArtistId.get(a.getId());
                return artistCount != null ? artistCount : 0;
            }).max().orElse(0);
            int artistScore = bestArtistScore == 0 ? maxArtistScore : (int) (maxArtistCount * maxArtistScore / bestArtistScore);
            int popularityScore = bestPopularity == 0 ? 10 : track.getPopularity() * 10 / bestPopularity;
            String name = track.getName().toLowerCase();
            int editDistance = levenshteinDistance.apply(trackName, name);
            int editDistanceScore = maxEditDistance == 0 ? 10 : 10 - (editDistance * 10 / maxEditDistance);

            if (name.contains(trackName) || trackName.contains(name)) {
                editDistanceScore += 10;
            }

            int totalScore = artistScore + popularityScore + editDistanceScore;
            tracksByScore.put(totalScore, track);
        }

        int bestScore = tracksByScore.keySet().stream().mapToInt(k -> k).max().getAsInt();
        return tracksByScore
            .get(bestScore)
            .stream()
            .max(Comparator.comparing(Track::getPopularity))
            .get();
    }

    /**
     * @return how many times each found artist id was played in this guild
     */
    private Map<String, Long> getPlaybackCountForArtists(Collection<Track> tracks) {
        Set<String> artistIds = tracks.stream()
            .flatMap(track -> Arrays.stream(track.getArtists()))
            .map(ArtistSimplified::getId)
            .collect(Collectors.toSet());
        String artistIdString = StringList.create(artistIds).applyForEach(s -> s = "'" + s + "'").toSeparatedString(", ");

        Query<Object[]> artistPopularityQuery = session.createNativeQuery("select a.id, count(*) from playback_history_artist as p " +
            "left join artist as a on p.artist_pk = a.pk " +
            "where p.playback_history_pk in (select pk from playback_history where guild_id = '" + guild.getId() + "') " +
            "and a.id in(" + artistIdString + ") group by a.id", Object[].class);
        List<Object[]> artistPopularityList = artistPopularityQuery.getResultList();

        Map<String, Long> playbackCountWithArtistId = new HashMap<>();
        for (Object[] columns : artistPopularityList) {
            playbackCountWithArtistId.put((String) columns[0], (Long) columns[1]);
        }

        return playbackCountWithArtistId;
    }

    /**
     * @param searchTerm the search term as entered by the user
     * @param found      all found tracks
     * @return the search term stripped to the track name, omitting filters such as "artist:"
     * e.g. "album:hybrid theory pushing me away" -> "pushing me away"
     */
    private String extractSearchedTrackName(String searchTerm, Collection<Track> found) {
        String trackName = searchTerm;
        if (searchTerm.contains("artist:")) {
            Set<String> artistNames = found.stream()
                .flatMap(track -> Arrays.stream(track.getArtists()))
                .map(ArtistSimplified::getName)
                .collect(Collectors.toSet());
            for (String artistName : artistNames) {
                String s = trackName.replaceAll("(?i)artist:([ \\t]+)*(?i)" + artistName, "");
                if (!s.equals(trackName)) {
                    trackName = s;
                    break;
                }
            }
        }

        if (searchTerm.contains("album:")) {
            Set<String> albumNames = found.stream().map(track -> track.getAlbum().getName()).collect(Collectors.toSet());

            for (String albumName : albumNames) {
                String s = trackName.replaceAll("album:([ \\t]+)*(?i)" + albumName, "");
                if (!s.equals(trackName)) {
                    trackName = s;
                    break;
                }
            }
        }

        return trackName.trim();
    }

}

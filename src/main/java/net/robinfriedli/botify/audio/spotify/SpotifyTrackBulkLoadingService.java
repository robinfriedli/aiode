package net.robinfriedli.botify.audio.spotify;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.function.CheckedFunction;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.botify.util.BulkOperationService;

/**
 * BulkOperationService extension that loads 50 Spotify tracks per request and performs the mapped action for each loaded
 * track. Note that {@link BulkOperationService#perform()} must be called with Spotify Credentials in this case, see
 * {@link SpotifyAuthorizationMode}.
 */
public class SpotifyTrackBulkLoadingService extends BulkOperationService<String, Track> {

    public SpotifyTrackBulkLoadingService(SpotifyApi spotifyApi) {
        this(spotifyApi, false);
    }

    public SpotifyTrackBulkLoadingService(SpotifyApi spotifyApi, boolean acceptNullValues) {
        super(50, (CheckedFunction<List<String>, List<Pair<String, Track>>>) ids -> {
            String[] idArray = ids.toArray(new String[0]);
            Track[] loadedTracks = spotifyApi.getSeveralTracks(idArray).build().execute();

            List<Pair<String, Track>> keyValuePairs = Lists.newArrayList();
            for (int i = 0; i < loadedTracks.length; i++) {
                Track loadedTrack = loadedTracks[i];

                if (loadedTrack != null) {
                    keyValuePairs.add(Pair.of(loadedTrack.getId(), loadedTrack));
                } else if (acceptNullValues) {
                    keyValuePairs.add(Pair.of(idArray[i], loadedTrack));
                }
            }

            return keyValuePairs;
        });
    }
}

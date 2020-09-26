package net.robinfriedli.botify.audio.spotify;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
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
        super(50, new CheckedFunction<>() {

            // time of first request, spotify credentials expire after and hour so if the task takes too long the credentials have to be refreshed
            private final LocalDateTime conceptionTime = LocalDateTime.now();
            private LocalDateTime timeToRefreshCredentials = conceptionTime.plusMinutes(50);

            @Override
            public List<Pair<String, Track>> doApply(List<String> ids) throws IOException, SpotifyWebApiException {
                LocalDateTime now = LocalDateTime.now();
                if (now.compareTo(timeToRefreshCredentials) > 0) {
                    ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
                    spotifyApi.setAccessToken(credentials.getAccessToken());
                    timeToRefreshCredentials = now.plusMinutes(50);
                }

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
            }
        });
    }

    /**
     * When persisting unavailable spotify tracks to a playlist their id is null, skip those tracks when loading
     */
    @Override
    public void add(String key, Consumer<Track> action) {
        if (key != null) {
            super.add(key, action);
        }
    }
}

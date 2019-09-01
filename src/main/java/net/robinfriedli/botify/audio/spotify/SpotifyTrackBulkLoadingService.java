package net.robinfriedli.botify.audio.spotify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.function.CheckedFunction;
import net.robinfriedli.botify.util.BulkOperationService;

public class SpotifyTrackBulkLoadingService extends BulkOperationService<String, Track> {

    public SpotifyTrackBulkLoadingService(SpotifyApi spotifyApi) {
        super(50, (CheckedFunction<List<String>, Map<Track, String>>) ids -> {
            String[] idArray = ids.toArray(new String[0]);
            Track[] loadedTracks = spotifyApi.getSeveralTracks(idArray).build().execute();

            Map<Track, String> trackMap = new LinkedHashMap<>();
            for (Track loadedTrack : loadedTracks) {
                if (loadedTrack == null) {
                    continue;
                }

                trackMap.put(loadedTrack, loadedTrack.getId());
            }

            return trackMap;
        });
    }
}

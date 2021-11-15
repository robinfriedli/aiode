package net.robinfriedli.aiode.boot.tasks;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.hc.core5.http.ParseException;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Track;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * As of botify 1.2, the track name and artist is now separated for videos that are redirected Spotify tracks rather than
 * combined in the title. This means that all videos that have the redirectedSpotifyId set receive a new attribute
 * spotifyTrackName.
 */
public class SetRedirectedSpotifyTrackNameTask implements StartupTask {

    private final JxpBackend jxpBackend;
    private final SpotifyApi spotifyApi;
    private final StartupTaskContribution contribution;

    public SetRedirectedSpotifyTrackNameTask(JxpBackend jxpBackend, SpotifyApi spotifyApi, StartupTaskContribution contribution) {
        this.jxpBackend = jxpBackend;
        this.spotifyApi = spotifyApi;
        this.contribution = contribution;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        ClientCredentials clientCredentials = spotifyApi.clientCredentials().build().execute();
        spotifyApi.setAccessToken(clientCredentials.getAccessToken());
        File file = new File("src/main/resources/playlists.xml");

        if (file.exists()) {
            try (Context context = jxpBackend.getContext(file)) {
                context.invoke(() -> {
                    try {
                        migrate(context, spotifyApi);
                    } catch (IOException | SpotifyWebApiException | ParseException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        spotifyApi.setAccessToken(null);
    }

    private void migrate(Context context, SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {
        for (XmlElement playlist : context.query(tagName("playlist")).collect()) {
            Map<String, XmlElement> itemsToMigrate = new HashMap<>();
            for (XmlElement playlistItem : playlist.getSubElements()) {
                if (playlistItem.hasAttribute("redirectedSpotifyId")
                    && !playlistItem.hasAttribute("spotifyTrackName")) {
                    String trackId = playlistItem.getAttribute("redirectedSpotifyId").getValue();
                    itemsToMigrate.put(trackId, playlistItem);
                }
            }

            List<List<String>> batches = Lists.partition(Lists.newArrayList(itemsToMigrate.keySet()), 50);
            for (List<String> batch : batches) {
                Track[] tracks = spotifyApi.getSeveralTracks(batch.toArray(new String[0])).build().execute();
                for (Track track : tracks) {
                    XmlElement playlistItem = itemsToMigrate.get(track.getId());
                    playlistItem.setAttribute("spotifyTrackName", track.getName());
                }
            }
        }

    }

}

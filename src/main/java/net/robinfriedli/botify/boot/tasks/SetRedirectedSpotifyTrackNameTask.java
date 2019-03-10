package net.robinfriedli.botify.boot.tasks;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;

/**
 * As of botify 1.2, the track name and artist is now separated for videos that are redirected Spotify tracks rather than
 * combined in the title. This means that all videos that have the redirectedSpotifyId set receive a new attribute
 * spotifyTrackName.
 */
public class SetRedirectedSpotifyTrackNameTask implements StartupTask {

    @Override
    public void perform(JxpBackend jxpBackend, JDA jda, SpotifyApi spotifyApi, YouTubeService youTubeService, Session session) throws Exception {
        ClientCredentials clientCredentials = spotifyApi.clientCredentials().build().execute();
        spotifyApi.setAccessToken(clientCredentials.getAccessToken());
        String playlistsPath = PropertiesLoadingService.requireProperty("PLAYLISTS_PATH");
        List<File> files = Lists.newArrayList();
        files.add(new File(playlistsPath));
        for (Guild guild : jda.getGuilds()) {
            String guildPlaylistsPath = PropertiesLoadingService.requireProperty("GUILD_PLAYLISTS_PATH", guild.getId());
            files.add(new File(guildPlaylistsPath));
        }

        for (File file : files) {
            if (file.exists()) {
                try (Context context = jxpBackend.getContext(file)) {
                    context.invoke(() -> {
                        try {
                            migrate(context, spotifyApi);
                        } catch (IOException | SpotifyWebApiException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
        spotifyApi.setAccessToken(null);
    }

    private void migrate(Context context, SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        for (XmlElement playlist : context.query(elem -> elem.getTagName().equals("playlist")).collect()) {
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

package net.robinfriedli.aiode.audio.exec;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import net.robinfriedli.aiode.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackRedirect;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.hibernate.Session;

public class SpotifyTrackRedirectionRunnable implements TrackLoadingRunnable<SpotifyTrackRedirect> {

    private final List<SpotifyTrackRedirect> tracksToRedirect;
    private final FilebrokerApi filebrokerApi;
    private final YouTubeService youTubeService;

    private SpotifyRedirectService spotifyRedirectService;

    public SpotifyTrackRedirectionRunnable(FilebrokerApi filebrokerApi, YouTubeService youTubeService, SpotifyTrackRedirect... tracksToRedirect) {
        this(filebrokerApi, Lists.newArrayList(tracksToRedirect), youTubeService);
    }

    public SpotifyTrackRedirectionRunnable(FilebrokerApi filebrokerApi, List<SpotifyTrackRedirect> tracksToRedirect, YouTubeService youTubeService) {
        this.filebrokerApi = filebrokerApi;
        this.tracksToRedirect = tracksToRedirect;
        this.youTubeService = youTubeService;
    }

    @Override
    public void addItems(Collection<SpotifyTrackRedirect> items) {
        tracksToRedirect.addAll(items);
    }

    @Override
    public List<SpotifyTrackRedirect> getItems() {
        return tracksToRedirect;
    }

    @Override
    public void handleCancellation() {
        tracksToRedirect.stream().filter(track -> !track.isDone()).forEach(SpotifyTrackRedirect::cancel);
    }

    @Override
    public void loadItem(SpotifyTrackRedirect item) throws Exception {
        if (spotifyRedirectService != null) {
            spotifyRedirectService.redirectTrack(item);
        } else {
            StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(filebrokerApi, session, youTubeService);
                spotifyRedirectService.redirectTrack(item);
            });
        }
    }

    @Override
    public void doRun() {
        if (!tracksToRedirect.isEmpty()) {
            StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                spotifyRedirectService = new SpotifyRedirectService(filebrokerApi, session, youTubeService);
                loadItems();
            });
        }
    }
}

package net.robinfriedli.aiode.audio.exec;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import net.robinfriedli.aiode.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;

public class SpotifyTrackRedirectionRunnable implements TrackLoadingRunnable<HollowYouTubeVideo> {

    private final List<HollowYouTubeVideo> tracksToRedirect;
    private final YouTubeService youTubeService;

    private SpotifyRedirectService spotifyRedirectService;

    public SpotifyTrackRedirectionRunnable(YouTubeService youTubeService, HollowYouTubeVideo... tracksToRedirect) {
        this(Lists.newArrayList(tracksToRedirect), youTubeService);
    }

    public SpotifyTrackRedirectionRunnable(List<HollowYouTubeVideo> tracksToRedirect, YouTubeService youTubeService) {
        this.tracksToRedirect = tracksToRedirect;
        this.youTubeService = youTubeService;
    }

    @Override
    public void addItems(Collection<HollowYouTubeVideo> items) {
        tracksToRedirect.addAll(items);
    }

    @Override
    public List<HollowYouTubeVideo> getItems() {
        return tracksToRedirect;
    }

    @Override
    public void handleCancellation() {
        tracksToRedirect.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
    }

    @Override
    public void loadItem(HollowYouTubeVideo item) throws Exception {
        if (spotifyRedirectService != null) {
            spotifyRedirectService.redirectTrack(item);
        } else {
            StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(session, youTubeService);
                spotifyRedirectService.redirectTrack(item);
            });
        }
    }

    @Override
    public void doRun() {
        if (!tracksToRedirect.isEmpty()) {
            StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                spotifyRedirectService = new SpotifyRedirectService(session, youTubeService);
                loadItems();
            });
        }
    }
}

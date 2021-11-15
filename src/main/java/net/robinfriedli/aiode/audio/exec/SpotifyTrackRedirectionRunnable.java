package net.robinfriedli.aiode.audio.exec;

import java.util.Collection;

import net.robinfriedli.aiode.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.function.ChainableRunnable;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;

public class SpotifyTrackRedirectionRunnable extends ChainableRunnable {

    private final Collection<HollowYouTubeVideo> tracksToRedirect;
    private final YouTubeService youTubeService;

    public SpotifyTrackRedirectionRunnable(Collection<HollowYouTubeVideo> tracksToRedirect, YouTubeService youTubeService) {
        this.tracksToRedirect = tracksToRedirect;
        this.youTubeService = youTubeService;
    }

    @Override
    public void doRun() {
        if (!tracksToRedirect.isEmpty()) {
            StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(session, youTubeService);
                for (HollowYouTubeVideo youTubeVideo : tracksToRedirect) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracksToRedirect.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
                        break;
                    }
                    try {
                        spotifyRedirectService.redirectTrack(youTubeVideo);
                    } catch (Exception e) {
                        tracksToRedirect.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
                        throw e;
                    }
                }
            });
        }
    }
}

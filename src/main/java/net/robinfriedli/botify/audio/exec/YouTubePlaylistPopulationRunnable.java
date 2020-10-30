package net.robinfriedli.botify.audio.exec;

import java.util.Arrays;
import java.util.Collection;

import net.robinfriedli.botify.audio.youtube.YouTubePlaylist;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.function.ChainableRunnable;

public class YouTubePlaylistPopulationRunnable extends ChainableRunnable {

    private final Collection<YouTubePlaylist> youTubePlaylistsToLoad;
    private final YouTubeService youTubeService;

    public YouTubePlaylistPopulationRunnable(YouTubeService youTubeService, YouTubePlaylist... playlists) {
        this(Arrays.asList(playlists), youTubeService);
    }

    public YouTubePlaylistPopulationRunnable(Collection<YouTubePlaylist> youTubePlaylistsToLoad, YouTubeService youTubeService) {
        this.youTubePlaylistsToLoad = youTubePlaylistsToLoad;
        this.youTubeService = youTubeService;
    }

    @Override
    public void doRun() throws Exception {
        for (YouTubePlaylist youTubePlaylist : youTubePlaylistsToLoad) {
            if (Thread.currentThread().isInterrupted()) {
                youTubePlaylistsToLoad.forEach(YouTubePlaylist::cancelLoading);
                break;
            }

            try {
                youTubeService.populateList(youTubePlaylist);
            } catch (Exception e) {
                youTubePlaylistsToLoad.forEach(YouTubePlaylist::cancelLoading);
                throw e;
            }
        }
    }
}

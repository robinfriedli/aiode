package net.robinfriedli.aiode.audio.exec;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;

public class YouTubePlaylistPopulationRunnable implements TrackLoadingRunnable<YouTubePlaylist> {

    private final List<YouTubePlaylist> youTubePlaylistsToLoad;
    private final YouTubeService youTubeService;

    public YouTubePlaylistPopulationRunnable(YouTubeService youTubeService, YouTubePlaylist... playlists) {
        this(Arrays.asList(playlists), youTubeService);
    }

    public YouTubePlaylistPopulationRunnable(List<YouTubePlaylist> youTubePlaylistsToLoad, YouTubeService youTubeService) {
        this.youTubePlaylistsToLoad = youTubePlaylistsToLoad;
        this.youTubeService = youTubeService;
    }

    @Override
    public void addItems(Collection<YouTubePlaylist> items) {
        youTubePlaylistsToLoad.addAll(items);
    }

    @Override
    public List<YouTubePlaylist> getItems() {
        return youTubePlaylistsToLoad;
    }

    @Override
    public void handleCancellation() {
        youTubePlaylistsToLoad.forEach(YouTubePlaylist::cancelLoading);
    }

    @Override
    public void loadItem(YouTubePlaylist item) throws Exception {
        youTubeService.populateList(item);
    }
}

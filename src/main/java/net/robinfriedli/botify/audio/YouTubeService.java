package net.robinfriedli.botify.audio;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.command.commands.PlayCommand;
import net.robinfriedli.botify.command.commands.QueueCommand;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

public class YouTubeService {

    private final YouTube youTube;
    private final String apiKey;

    public YouTubeService(YouTube youTube, String apiKey) {
        this.youTube = youTube;
        this.apiKey = apiKey;
    }

    /**
     * Workaround as Spotify does not allow full playback of tracks via third party APIs using the web api for licencing
     * reasons. Gets the metadata and searches the corresponding YouTube video. The only way to stream from Spotify
     * directly is by using the $preview argument with the {@link PlayCommand} or {@link QueueCommand} which plays the
     * provided mp3 preview.
     *
     * However Spotify MIGHT release an SDK supporting full playback of songs across all devices, not just browsers in
     * which case this method and the corresponding black in {@link AudioManager#createPlayable(boolean, Object)} should
     * be removed.
     * For reference, see <a href="https://github.com/spotify/web-api/issues/57">Web playback of Full Tracks - Github</a>
     *
     * @param youTubeVideo the hollow youtube that has already been added to the queue and awaits to receive values
     * @return the URL for the matching YouTube video
     */
    public void redirectSpotify(HollowYouTubeVideo youTubeVideo) {
        Track spotifyTrack = youTubeVideo.getRedirectedSpotifyTrack();

        if (spotifyTrack == null) {
            throw new IllegalArgumentException(youTubeVideo.toString() + " is not a placeholder for a redirected Spotify Track");
        }

        try {
            YouTube.Search.List search = youTube.search().list("id,snippet");
            search.setKey(apiKey);

            StringList artists = StringListImpl.create(spotifyTrack.getArtists(), ArtistSimplified::getName);
            String searchTerm = spotifyTrack.getName() + " " + artists.toSeparatedString(" ");
            search.setQ(searchTerm);
            // set topic to filter results to music video
            search.setTopicId("/m/04rlf");
            search.setType("video");
            search.setFields("items(snippet/title,id/videoId)");
            search.setMaxResults(1L);

            List<SearchResult> items = search.execute().getItems();
            if (items.isEmpty()) {
                throw new NoResultsFoundException("No YouTube Video found for track " + searchTerm);
            }
            SearchResult searchResult = items.get(0);
            String videoId = searchResult.getId().getVideoId();
            long durationMillis = getDurationMillis(videoId);

            String artistString = artists.toSeparatedString(", ");
            String title = spotifyTrack.getName() + " by " + artistString;
            youTubeVideo.setTitle(title);
            youTubeVideo.setId(videoId);
            youTubeVideo.setDuration(durationMillis);
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred during YouTube redirect", e);
        }
    }

    public YouTubeVideo searchVideo(String searchTerm) {
        try {
            List<SearchResult> items = searchVideos(1, searchTerm);
            SearchResult searchResult = items.get(0);
            String videoId = searchResult.getId().getVideoId();
            String title = searchResult.getSnippet().getTitle();
            long durationMillis = getDurationMillis(videoId);

            return new YouTubeVideoImpl(title, videoId, durationMillis);
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred during YouTube search", e);
        }
    }

    public List<YouTubeVideo> searchSeveralVideos(long limit, String searchTerm) {
        try {
            List<SearchResult> searchResults = searchVideos(limit, searchTerm);
            List<String> videoIds = searchResults.stream().map(result -> result.getId().getVideoId()).collect(Collectors.toList());
            Map<String, Long> allDurations = getAllDurations(videoIds);
            List<YouTubeVideo> videos = Lists.newArrayList();

            for (SearchResult searchResult : searchResults) {
                String videoId = searchResult.getId().getVideoId();
                String title = searchResult.getSnippet().getTitle();
                Long duration = allDurations.get(videoId);

                videos.add(new YouTubeVideoImpl(title, videoId, duration));
            }

            return videos;
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred during YouTube search", e);
        }
    }

    private List<SearchResult> searchVideos(long limit, String searchTerm) throws IOException {
        YouTube.Search.List search = youTube.search().list("id,snippet");
        search.setQ(searchTerm);
        search.setType("video");
        search.setFields("items(snippet/title,id/videoId)");
        search.setMaxResults(limit);
        search.setKey(apiKey);

        List<SearchResult> items = search.execute().getItems();
        if (items.isEmpty()) {
            throw new NoResultsFoundException("No YouTube video found for " + searchTerm);
        }

        return items;
    }

    private Map<String, Long> getAllDurations(List<String> videoIds) throws IOException {
        Map<String, Long> durationMap = new HashMap<>();
        List<List<String>> sequences = Lists.partition(videoIds, 50);
        for (List<String> sequence : sequences) {
            durationMap.putAll(getDurationMillis(sequence));
        }

        return durationMap;
    }

    public YouTubePlaylist searchPlaylist(String searchTerm) {
        try {
            List<SearchResult> items = searchPlaylists(1, searchTerm);
            SearchResult searchResult = items.get(0);
            String playlistId = searchResult.getId().getPlaylistId();
            String title = searchResult.getSnippet().getTitle();
            String channelTitle = searchResult.getSnippet().getChannelTitle();

            int itemCount = youTube
                .playlists()
                .list("contentDetails")
                .setKey(apiKey)
                .setId(playlistId)
                .setFields("items(contentDetails/itemCount)")
                .setMaxResults(1L)
                .execute()
                .getItems()
                .get(0)
                .getContentDetails()
                .getItemCount()
                .intValue();

            // return hollow youtube videos so that the playlist items can be loaded asynchronously
            List<HollowYouTubeVideo> videos = Lists.newArrayListWithCapacity(itemCount);
            for (int i = 0; i < itemCount; i++) {
                videos.add(new HollowYouTubeVideo(this));
            }

            return new YouTubePlaylist(title, playlistId, channelTitle, videos);
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred during YouTube search", e);
        }
    }

    public List<YouTubePlaylist> searchSeveralPlaylists(long limit, String searchTerm) {
        try {
            List<SearchResult> items = searchPlaylists(limit, searchTerm);
            List<String> playlistIds = items.stream().map(item -> item.getId().getPlaylistId()).collect(Collectors.toList());
            Map<String, Long> playlistItemCounts = getAllPlaylistItemCounts(playlistIds);
            List<YouTubePlaylist> playlists = Lists.newArrayList();

            for (SearchResult item : items) {
                String title = item.getSnippet().getTitle();
                String channelTitle = item.getSnippet().getChannelTitle();
                String playlistId = item.getId().getPlaylistId();
                Long longCount = playlistItemCounts.get(playlistId);
                int itemCount = longCount != null ? longCount.intValue() : 0;
                List<HollowYouTubeVideo> videos = Lists.newArrayListWithCapacity(itemCount);
                for (int i = 0; i < itemCount; i++) {
                    videos.add(new HollowYouTubeVideo(this));
                }

                playlists.add(new YouTubePlaylist(title, playlistId, channelTitle, videos));
            }

            return playlists;
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred during YouTube search", e);
        }
    }

    private List<SearchResult> searchPlaylists(long limit, String searchTerm) throws IOException {
        YouTube.Search.List playlistSearch = youTube.search().list("id,snippet");
        playlistSearch.setKey(apiKey);
        playlistSearch.setQ(searchTerm);
        playlistSearch.setType("playlist");
        playlistSearch.setFields("items(id/playlistId,snippet/title,snippet/channelTitle)");
        playlistSearch.setMaxResults(limit);

        List<SearchResult> items = playlistSearch.execute().getItems();
        if (items.isEmpty()) {
            throw new NoResultsFoundException("No YouTube playlist found for " + searchTerm);
        }

        return items;
    }

    private Map<String, Long> getAllPlaylistItemCounts(List<String> playlistIds) throws IOException {
        Map<String, Long> itemCounts = new HashMap<>();
        List<List<String>> sequences = Lists.partition(playlistIds, 50);
        for (List<String> sequence : sequences) {
            List<Playlist> playlists = youTube
                .playlists()
                .list("contentDetails")
                .setKey(apiKey)
                .setId(String.join(",", sequence))
                .execute()
                .getItems();
            for (Playlist playlist : playlists) {
                itemCounts.put(playlist.getId(), playlist.getContentDetails().getItemCount());
            }
        }

        return itemCounts;
    }

    public void populateList(YouTubePlaylist playlist) {
        try {
            YouTube.PlaylistItems.List itemSearch = youTube.playlistItems().list("snippet");
            itemSearch.setKey(apiKey);
            itemSearch.setMaxResults(50L);
            itemSearch.setFields("items(snippet/title,snippet/resourceId),nextPageToken");
            itemSearch.setPlaylistId(playlist.getId());

            String nextPageToken;
            List<PlaylistItem> playlistItems = Lists.newArrayList();
            List<HollowYouTubeVideo> hollowVideos = playlist.getVideos();
            int index = 0;
            do {
                PlaylistItemListResponse response = itemSearch.execute();
                nextPageToken = response.getNextPageToken();
                List<PlaylistItem> items = response.getItems();
                playlistItems.addAll(items);

                List<HollowYouTubeVideo> currentVideos = Lists.newArrayList();
                for (PlaylistItem item : items) {
                    String videoTitle = item.getSnippet().getTitle();
                    String videoId = item.getSnippet().getResourceId().getVideoId();
                    HollowYouTubeVideo hollowVideo = hollowVideos.get(index);
                    hollowVideo.setTitle(videoTitle);
                    hollowVideo.setId(videoId);
                    currentVideos.add(hollowVideo);
                    ++index;
                }
                loadDurationsAsync(currentVideos);

                itemSearch.setPageToken(nextPageToken);

                if (Thread.currentThread().isInterrupted()) {
                    playlist.cancelLoading();
                    return;
                }
            } while (!Strings.isNullOrEmpty(nextPageToken));

            if (playlistItems.isEmpty()) {
                throw new NoResultsFoundException("Playlist " + playlist.getTitle() + " has no items");
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred while loading playlist items", e);
        }
    }

    private void loadDurationsAsync(List<HollowYouTubeVideo> videos) {
        // ids have already been loaded in other thread
        List<String> videoIds = videos.stream().map(HollowYouTubeVideo::getId).collect(Collectors.toList());
        // TrackLoadingExceptionHandler
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
        Thread durationLoadingThread = new Thread(() -> {
            try {
                Map<String, Long> durationMillis = getDurationMillis(videoIds);
                for (HollowYouTubeVideo video : videos) {
                    Long duration = durationMillis.get(video.getId());
                    video.setDuration(duration != null ? duration : 0);
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception occurred while loading durations", e);
            }
        });
        durationLoadingThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        durationLoadingThread.setName("Botify duration loading");
        durationLoadingThread.start();
    }

    /**
     * Load a specific playlist item used when the {@link #populateList(YouTubePlaylist)} has not loaded the requested
     * item yet. This is not very efficient since several requests have to be made to find the appropriate page token
     * but it's necessary if shuffle is enabled when loading a large playlist as the populateList methods might take a
     * while until the items towards the end of the list are loaded.
     *
     * @param index the index of the item to load
     * @param playlist the playlist the item is a part of
     */
    public void loadPlaylistItem(int index, YouTubePlaylist playlist) {
        if (index < 0 || index >= playlist.getVideos().size()) {
            throw new IllegalArgumentException("Index " + index + " out of bounds for list " + playlist.getTitle());
        }

        int page = index / 50;
        int currentPage = 0;

        try {
            String pageToken = null;
            if (page > 0) {
                YouTube.PlaylistItems.List tokenSearch = youTube.playlistItems().list("id");
                tokenSearch.setMaxResults(50L);
                tokenSearch.setFields("nextPageToken");
                tokenSearch.setPlaylistId(playlist.getId());
                tokenSearch.setKey(apiKey);
                while (currentPage < page) {
                    pageToken = tokenSearch.execute().getNextPageToken();
                    tokenSearch.setPageToken(pageToken);

                    if (pageToken == null) {
                        //should not happen unless page was calculated incorrectly
                        throw new IllegalStateException("Page token search went out of bounds when searching playlist item. Expected more pages.");
                    }

                    ++currentPage;
                }
            }

            YouTube.PlaylistItems.List itemSearch = youTube.playlistItems().list("snippet");
            itemSearch.setMaxResults(50L);
            itemSearch.setFields("items(snippet/title,snippet/resourceId)");
            itemSearch.setPlaylistId(playlist.getId());
            itemSearch.setKey(apiKey);
            if (pageToken != null) {
                itemSearch.setPageToken(pageToken);
            }
            List<PlaylistItem> playlistItems = itemSearch.execute().getItems();

            // get the index the item has on the current page (value between 0-50)
            // e.g the item at index 123 is the item with index 23 on the third page (page 2)
            int indexOnPage = index - page * 50;
            PlaylistItem playlistItem = playlistItems.get(indexOnPage);
            String videoId = playlistItem.getSnippet().getResourceId().getVideoId();
            String title = playlistItem.getSnippet().getTitle();
            HollowYouTubeVideo hollowYouTubeVideo = playlist.getVideos().get(index);
            hollowYouTubeVideo.setId(videoId);
            hollowYouTubeVideo.setTitle(title);
            hollowYouTubeVideo.setDuration(getDurationMillis(videoId));
        } catch (IOException e) {
            throw new RuntimeException("Exception occurred while loading playlist item " + index + " for list " + playlist.getTitle(), e);
        }
    }

    /**
     * Calls the video source to retrieve its duration in milliseconds
     *
     * @param videoId the id of the video
     * @return the video's duration in milliseconds
     */
    private long getDurationMillis(String videoId) throws IOException {
        YouTube.Videos.List videosRequest = youTube.videos().list("contentDetails");
        videosRequest.setKey(apiKey);
        videosRequest.setId(videoId);
        videosRequest.setFields("items(contentDetails/duration)");
        VideoListResponse videoListResponse = videosRequest.execute();
        List<Video> items = videoListResponse.getItems();
        if (items.size() == 1) {
            String iso8601Duration = items.get(0).getContentDetails().getDuration();
            // ChronoUnit.MILLIS not supported because of the accuracy YouTube returns
            return Duration.parse(iso8601Duration).get(ChronoUnit.SECONDS) * 1000;
        } else {
            // video detail might not get found if the video is unavailable
            return 0;
        }
    }

    private Map<String, Long> getDurationMillis(Collection<String> videoIds) throws IOException {
        if (videoIds.size() > 50) {
            throw new IllegalArgumentException("Cannot request more than 50 ids at once");
        }

        YouTube.Videos.List videosRequest = youTube.videos().list("contentDetails");
        videosRequest.setKey(apiKey);
        videosRequest.setId(String.join(",", videoIds));
        videosRequest.setFields("items(contentDetails/duration,id)");
        List<Video> videos = videosRequest.execute().getItems();

        Map<String, Long> durationMap = new HashMap<>();
        for (Video video : videos) {
            String iso8601Duration = video.getContentDetails().getDuration();
            String id = video.getId();
            durationMap.put(id, Duration.parse(iso8601Duration).get(ChronoUnit.SECONDS) * 1000);
        }

        return durationMap;
    }

}

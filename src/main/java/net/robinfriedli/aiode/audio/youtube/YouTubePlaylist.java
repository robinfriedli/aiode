package net.robinfriedli.aiode.audio.youtube;

import java.util.List;

/**
 * Class representing a YouTube playlist. At its conception none of its videos have been loaded yet, this is typically
 * done asynchronously, see {@link YouTubeService#populateList(YouTubePlaylist)}
 */
public class YouTubePlaylist {

    private final String title;
    private final String id;
    private final String url;
    private final String channelTitle;
    private final List<HollowYouTubeVideo> videos;
    private final boolean isPreLoaded;

    public YouTubePlaylist(String title, String id, String channelTitle, List<HollowYouTubeVideo> videos) {
        this(title, id, channelTitle, videos, false);
    }

    public YouTubePlaylist(String title, String id, String channelTitle, List<HollowYouTubeVideo> videos, boolean isPreLoaded) {
        this.title = title;
        this.id = id;
        this.url = String.format("https://www.youtube.com/playlist?list=%s", id);
        this.channelTitle = channelTitle;
        this.videos = videos;
        this.isPreLoaded = isPreLoaded;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public List<HollowYouTubeVideo> getVideos() {
        return videos;
    }

    public void cancelLoading() {
        videos.stream().filter(HollowYouTubeVideo::isHollow).forEach(HollowYouTubeVideo::cancel);
    }

    public boolean isPreLoaded() {
        return isPreLoaded;
    }
}

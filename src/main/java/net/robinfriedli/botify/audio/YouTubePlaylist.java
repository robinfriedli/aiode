package net.robinfriedli.botify.audio;

import java.util.List;

public class YouTubePlaylist {

    private final String title;
    private final String id;
    private final String url;
    private final String channelTitle;
    private final List<YouTubeVideo> videos;
    private final long durationMs;

    public YouTubePlaylist(String title, String id, String channelTitle, List<YouTubeVideo> videos) {
        this.title = title;
        this.id = id;
        this.url = String.format("https://www.youtube.com/playlist?list=%s", id);
        this.channelTitle = channelTitle;
        this.videos = videos;
        durationMs = videos.stream().mapToLong(YouTubeVideo::getDuration).sum();
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

    public List<YouTubeVideo> getVideos() {
        return videos;
    }

    public long getDurationMs() {
        return durationMs;
    }
}

package net.robinfriedli.botify.audio;

import java.util.List;

public class YouTubePlaylist {

    private final String title;
    private final String id;
    private final String url;
    private final String channelTitle;
    private final List<HollowYouTubeVideo> videos;

    public YouTubePlaylist(String title, String id, String channelTitle, List<HollowYouTubeVideo> videos) {
        this.title = title;
        this.id = id;
        this.url = String.format("https://www.youtube.com/playlist?list=%s", id);
        this.channelTitle = channelTitle;
        this.videos = videos;
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

}

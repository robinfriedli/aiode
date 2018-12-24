package net.robinfriedli.botify.entities;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class Video extends AbstractXmlElement {

    public Video(YouTubeVideo video, User addedUser, Context context) {
        super("video", getAttributeMap(video, addedUser, null), context);
    }

    public Video(YouTubeVideo video, User addedUser, @Nullable Track redirectedSpotifyTrack, Context context) {
        super("video", getAttributeMap(video, addedUser, redirectedSpotifyTrack), context);
    }

    @SuppressWarnings("unused")
    // invoked by JXP
    public Video(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("id").getValue();
    }

    public YouTubeVideo asYouTubeVideo() {
        return new YouTubeVideo(
            getAttribute("title").getValue(),
            getAttribute("id").getValue(),
            getAttribute("duration").getValue(Long.class)
        );
    }

    private static Map<String, ?> getAttributeMap(YouTubeVideo video, User addedUser, @Nullable Track redirectedSpotifyTrack) {
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("id", video.getId());
        attributeMap.put("title", video.getTitle());
        attributeMap.put("duration", video.getDuration());
        attributeMap.put("addedUser", addedUser.getName());
        attributeMap.put("addedUserId", addedUser.getId());
        if (redirectedSpotifyTrack != null) {
            attributeMap.put("redirectedSpotifyId", redirectedSpotifyTrack.getId());
        }

        return attributeMap;
    }
}

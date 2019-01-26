package net.robinfriedli.botify.entities;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.PlayableImpl;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class Playlist extends AbstractXmlElement {

    public Playlist(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    public Playlist(String name, User createdUser, List<XmlElement> tracks, Context context) {
        super("playlist", getAttributeMap(name, createdUser, tracks), Lists.newArrayList(tracks), context);
    }

    @SuppressWarnings("unused")
    // invoked by JXP
    public Playlist(Element element, Context context) {
        super(element, context);
    }

    @SuppressWarnings("unused")
    // invoked by JXP
    public Playlist(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("name").getValue();
    }

    /**
     * Returns the items in this playlist as objects supported by the {@link PlayableImpl} class. Note that getting the
     * Spotify track for a Song requires this method to be invoked with client credentials
     */
    public List<Object> getItems(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        List<Object> items = Lists.newArrayList();
        List<String> trackIds = Lists.newArrayList();
        for (XmlElement item : getSubElements()) {
            if (item instanceof Song) {
                trackIds.add(item.getAttribute("id").getValue());
            } else if (item instanceof Video) {
                items.add(((Video) item).asYouTubeVideo());
            } else if (item instanceof UrlTrack) {
                items.add(item);
            }
        }

        List<List<String>> batches = Lists.partition(trackIds, 50);
        for (List<String> batch : batches) {
            Track[] tracks = spotifyApi.getSeveralTracks(batch.toArray(new String[0])).build().execute();
            items.addAll(Arrays.asList(tracks));
        }

        return items;
    }

    /**
     * returns all Songs as Spotify tracks including all videos that are redirected Spotify tracks i.e. the attribute
     * redirectedSpotifyId is set. Mind that this method has to be invoked with client credentials
     */
    public List<Track> asTrackList(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        List<Track> tracks = Lists.newArrayList();
        for (XmlElement track : getSubElements()) {
            if (track instanceof Song) {
                tracks.add(spotifyApi.getTrack(track.getAttribute("id").getValue()).build().execute());
            } else if (track instanceof Video && track.hasAttribute("redirectedSpotifyId")) {
                tracks.add(spotifyApi.getTrack(track.getAttribute("redirectedSpotifyId").getValue()).build().execute());
            }
        }

        return tracks;
    }

    private static Map<String, ?> getAttributeMap(String name, User createdUser, List<XmlElement> tracks) {
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("name", name);

        long duration = 0;
        for (XmlElement track : tracks) {
            duration = duration + track.getAttribute("duration").getLong();
        }

        attributeMap.put("duration", duration);
        attributeMap.put("songCount", tracks.size());
        attributeMap.put("createdUser", createdUser.getName());
        attributeMap.put("createdUserId", createdUser.getId());

        return attributeMap;
    }
}

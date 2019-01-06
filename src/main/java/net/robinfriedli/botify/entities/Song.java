package net.robinfriedli.botify.entities;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.stringlist.StringListImpl;
import org.w3c.dom.Element;

public class Song extends AbstractXmlElement {

    public Song(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    public Song(Track track, User addedUser, Context context) {
        super("song", getAttributeMap(track, addedUser), context);
    }

    @SuppressWarnings("unused")
    // invoked by JXP
    public Song(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("id").getValue();
    }

    public Track asTrack(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException {
        return spotifyApi.getTrack(getAttribute("id").getValue()).build().execute();
    }

    private static Map<String, ?> getAttributeMap(Track track, User addedUser) {
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("id", track.getId());
        attributeMap.put("name", track.getName());
        attributeMap.put(
            "artists",
            StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(",")
        );
        attributeMap.put(
            "artistIds",
            StringListImpl.create(track.getArtists(), ArtistSimplified::getId).toSeparatedString(",")
        );
        attributeMap.put("duration", track.getDurationMs());
        attributeMap.put("addedUser", addedUser.getName());
        attributeMap.put("addedUserId", addedUser.getId());

        return attributeMap;
    }
}

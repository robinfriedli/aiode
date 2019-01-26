package net.robinfriedli.botify.entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.UrlPlayable;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class UrlTrack extends AbstractXmlElement {

    public UrlTrack(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    public UrlTrack(UrlPlayable playable, User addedUser, Context context) {
        super("urlTrack", getAttributeMap(playable, addedUser), context);
    }

    @SuppressWarnings("unused")
    // invoked by JXP
    public UrlTrack(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("id").getValue();
    }

    public UrlPlayable asPlayable() {
        return new UrlPlayable(this);
    }

    private static Map<String, ?> getAttributeMap(UrlPlayable playable, User addedUser) {
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("url", playable.getPlaybackUrl());
        attributeMap.put("title", playable.getDisplay());
        attributeMap.put("duration", playable.getDurationMs());
        attributeMap.put("addedUser", addedUser.getName());
        attributeMap.put("addedUserId", addedUser.getId());

        return attributeMap;
    }
}

package net.robinfriedli.botify.util;

import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.events.ElementCreatedEvent;
import net.robinfriedli.jxp.events.ElementDeletingEvent;
import net.robinfriedli.jxp.events.EventListener;

public class PlaylistListener extends EventListener {

    @Override
    public void elementCreating(ElementCreatedEvent event) {
        XmlElement source = event.getSource();
        if (source instanceof Song || source instanceof Video || source instanceof UrlTrack) {
            XmlElement playlist = source.getParent();
            playlist.setAttribute("songCount", playlist.getAttribute("songCount").getInt() + 1);
            playlist.setAttribute("duration",
                playlist.getAttribute("duration").getLong() + source.getAttribute("duration").getLong());
        }
    }

    @Override
    public void elementDeleting(ElementDeletingEvent event) {
        XmlElement source = event.getSource();
        if (source instanceof Song || source instanceof Video || source instanceof UrlTrack) {
            XmlElement playlist = event.getOldParent();
            //noinspection ConstantConditions
            playlist.setAttribute("songCount", playlist.getAttribute("songCount").getInt() - 1);
            playlist.setAttribute("duration",
                playlist.getAttribute("duration").getLong() - source.getAttribute("duration").getLong());
        }
    }

}

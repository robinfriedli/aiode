package net.robinfriedli.botify.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

public class UrlPlayable implements Playable {

    private final String url;
    private final String display;
    private final long duration;

    public UrlPlayable(AudioTrack audioTrack) {
        url = audioTrack.getInfo().uri;
        display = audioTrack.getInfo().title;
        duration = audioTrack.getDuration();
    }

    public UrlPlayable(UrlTrack urlTrack) {
        url = urlTrack.getAttribute("url").getValue();
        display = urlTrack.getAttribute("title").getValue();
        duration = urlTrack.getAttribute("duration").getLong();
    }

    @Override
    public String getPlaybackUrl() {
        return url;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public long getDurationMs() {
        return duration;
    }

    @Override
    public XmlElement export(Context context, User user) {
        return new UrlTrack(this, user, context);
    }
}

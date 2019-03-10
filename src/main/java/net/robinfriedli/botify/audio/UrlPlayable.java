package net.robinfriedli.botify.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.UrlTrack;
import org.hibernate.Session;

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
        url = urlTrack.getUrl();
        display = urlTrack.getTitle();
        duration = urlTrack.getDuration();
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
    public PlaylistItem export(Playlist playlist, User user, Session session) {
        return new UrlTrack(this, user, playlist);
    }

    @Override
    public String getSource() {
        return "Url";
    }

}

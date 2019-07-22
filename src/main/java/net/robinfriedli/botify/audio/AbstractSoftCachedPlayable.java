package net.robinfriedli.botify.audio;

import java.lang.ref.SoftReference;

import javax.annotation.Nullable;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * creates a soft reference to the resulting AudioTrack when playing a Playable
 */
public abstract class AbstractSoftCachedPlayable implements Playable {

    private SoftReference<AudioTrack> cachedTrack;

    @Nullable
    @Override
    public AudioTrack getCached() {
        if (cachedTrack != null) {
            return cachedTrack.get();
        }

        return null;
    }

    @Override
    public void setCached(AudioTrack audioTrack) {
        cachedTrack = new SoftReference<>(audioTrack);
    }
}

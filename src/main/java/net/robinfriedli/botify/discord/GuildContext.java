package net.robinfriedli.botify.discord;

import javax.annotation.Nullable;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.concurrent.Invoker;
import net.robinfriedli.botify.entities.GuildSpecification;

public class GuildContext {

    private final AudioPlayback playback;
    private final Invoker invoker;
    private final GuildSpecification specification;

    public GuildContext(AudioPlayback playback, GuildSpecification specification, @Nullable Invoker sharedInvoker) {
        this.playback = playback;
        this.specification = specification;
        invoker = sharedInvoker == null ? new Invoker() : sharedInvoker;
    }

    public AudioPlayback getPlayback() {
        return playback;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public GuildSpecification getSpecification() {
        return specification;
    }
}

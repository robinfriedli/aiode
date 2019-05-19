package net.robinfriedli.botify.discord;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.util.Invoker;

public class GuildContext {

    private final AudioPlayback playback;
    private final Invoker invoker;
    private final GuildSpecification specification;

    public GuildContext(AudioPlayback playback, GuildSpecification specification) {
        this.playback = playback;
        this.specification = specification;
        invoker = new Invoker();
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

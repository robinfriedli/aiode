package net.robinfriedli.aiode.audio.playables.containers

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.UrlPlayable
import net.robinfriedli.aiode.audio.playables.AbstractPlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component
import java.util.stream.Collectors

class AudioPlaylistPlayableContainer(audioPlaylist: AudioPlaylist) : AbstractPlayableContainer<AudioPlaylist>(audioPlaylist) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return getItem().tracks.stream().map { track -> UrlPlayable(track) }.collect(Collectors.toList())
    }
}

@Component
class AudioPlaylistPlayableContainerProvider : PlayableContainerProvider<AudioPlaylist> {
    override fun getType(): Class<AudioPlaylist> {
        return AudioPlaylist::class.java
    }

    override fun getPlayableContainer(item: AudioPlaylist): PlayableContainer<AudioPlaylist> {
        return AudioPlaylistPlayableContainer(item)
    }
}

package net.robinfriedli.aiode.audio.playables.containers

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.UrlPlayable
import net.robinfriedli.aiode.audio.playables.AbstractSinglePlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component

class AudioTrackPlayableContainer(audioTrack: AudioTrack) : AbstractSinglePlayableContainer<AudioTrack>(audioTrack) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return UrlPlayable(getItem())
    }
}

@Component
class AudioTrackPlayableContainerProvider : PlayableContainerProvider<AudioTrack> {
    override fun getType(): Class<AudioTrack> {
        return AudioTrack::class.java
    }

    override fun getPlayableContainer(item: AudioTrack): PlayableContainer<AudioTrack> {
        return AudioTrackPlayableContainer(item)
    }
}

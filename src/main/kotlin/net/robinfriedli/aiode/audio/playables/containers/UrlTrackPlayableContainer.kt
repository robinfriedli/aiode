package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractSinglePlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import net.robinfriedli.aiode.entities.UrlTrack
import org.springframework.stereotype.Component

class UrlTrackPlayableContainer(urlTrack: UrlTrack) : AbstractSinglePlayableContainer<UrlTrack>(urlTrack) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable? {
        return getItem().asPlayable()
    }
}

@Component
class UrlTrackPlayableContainerProvider : PlayableContainerProvider<UrlTrack> {
    override fun getType(): Class<UrlTrack> {
        return UrlTrack::class.java
    }

    override fun getPlayableContainer(item: UrlTrack): PlayableContainer<UrlTrack> {
        return UrlTrackPlayableContainer(item)
    }
}

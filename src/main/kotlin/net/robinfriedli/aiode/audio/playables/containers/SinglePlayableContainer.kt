package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractSinglePlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component

class SinglePlayableContainer(playable: Playable) : AbstractSinglePlayableContainer<Playable>(playable) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return getItem()
    }
}

@Component
class SinglePlayableContainerProvider : PlayableContainerProvider<Playable> {
    override fun getType(): Class<Playable> {
        return Playable::class.java
    }

    override fun getPlayableContainer(item: Playable): PlayableContainer<Playable> {
        return SinglePlayableContainer(item)
    }
}

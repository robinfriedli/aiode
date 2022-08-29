package net.robinfriedli.aiode.audio.playables

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.queue.AudioQueue
import net.robinfriedli.aiode.audio.queue.QueueFragment
import net.robinfriedli.aiode.audio.queue.SinglePlayableQueueFragment
import java.util.*

abstract class AbstractSinglePlayableContainer<T>(item: T) : AbstractPlayableContainer<T>(item) {

    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        val playable = doLoadPlayable(playableFactory)
        return if (playable != null) {
            Collections.singletonList(playable)
        } else {
            Collections.emptyList()
        }
    }

    override fun loadPlayable(playableFactory: PlayableFactory): Playable? {
        val playables = loadPlayables(playableFactory)
        return when {
            playables.size == 1 -> playables[0]
            playables.isNotEmpty() -> throw UnsupportedOperationException("PlayableContainer $this does not resolve to a single Playable")
            else -> null
        }
    }

    override fun createQueueFragment(playableFactory: PlayableFactory, queue: AudioQueue): QueueFragment? {
        val playable = loadPlayable(playableFactory)
        return if (playable != null) {
            SinglePlayableQueueFragment(queue, playable, this)
        } else {
            null
        }
    }

    abstract fun doLoadPlayable(playableFactory: PlayableFactory): Playable?

}

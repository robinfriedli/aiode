package net.robinfriedli.aiode.audio.queue

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractSinglePlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import java.util.*

class SinglePlayableQueueFragment(
    private val queue: AudioQueue,
    private val playable: Playable,
    private val playableContainer: AbstractSinglePlayableContainer<*>
) : QueueFragment {

    override fun size(): Int {
        return 1
    }

    override fun getPlayableContainer(): PlayableContainer<*> {
        return playableContainer
    }

    override fun getQueue(): AudioQueue {
        return queue
    }

    override fun addFracture(idx: Int): Int {
        throw UnsupportedOperationException("Cannot fracture ${javaClass.simpleName}")
    }

    override fun addFracture(fractureIdx: Int, idx: Int): Int {
        throw UnsupportedOperationException("Cannot fracture ${javaClass.simpleName}")
    }

    override fun getPlayables(): List<Playable> {
        return Collections.singletonList(playable)
    }

    override fun getPlayableInFracture(fractureIdx: Int, idx: Int): Playable {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else if (idx > 0) {
            throw IndexOutOfBoundsException("Playable index $idx out of bounds for ${javaClass.simpleName}")
        } else {
            playable
        }
    }

    override fun getPlayablesInFracture(fractureIdx: Int): List<Playable> {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            getPlayables()
        }
    }

    override fun sizeOfFracture(fractureIdx: Int): Int {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            1
        }
    }
}

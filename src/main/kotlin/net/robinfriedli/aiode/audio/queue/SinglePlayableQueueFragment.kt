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

    override fun currentIndex(): Int {
        return 0
    }

    override fun next(): Playable {
        return playable
    }

    override fun current(): Playable {
        return playable
    }

    override fun hasNext(fractureIdx: Int): Boolean {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            false
        }
    }

    override fun hasPrevious(fractureIdx: Int): Boolean {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            false
        }
    }

    override fun previous(): Playable {
        return playable
    }

    override fun peekNext(): Playable {
        return playable
    }

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

    override fun getOrderedFractures(): List<Int> {
        return Collections.emptyList()
    }

    override fun getPlayables(): List<Playable> {
        return Collections.singletonList(playable)
    }

    override fun getPlayablesInCurrentOrder(): List<Playable> {
        return getPlayables()
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

    override fun getNextPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable> {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            Collections.emptyList()
        }
    }

    override fun getPreviousPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable> {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            Collections.emptyList()
        }
    }

    override fun sizeOfFracture(fractureIdx: Int): Int {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            1
        }
    }

    override fun currentIndexWithinFracture(fractureIdx: Int): Int {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            currentIndex()
        }
    }

    override fun setPosition(fractureIdx: Int, offset: Int) {
        if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else if (offset > 0) {
            throw IndexOutOfBoundsException("Offset $offset out of bounds for ${javaClass.simpleName}")
        }
    }

    override fun resetPositionToStart() {
    }

    override fun resetPositionToEnd() {
    }

    override fun enableShuffle(protectCurrent: Boolean): Int {
        return 0
    }

    override fun disableShuffle() {
    }

    override fun reduceToCurrentPlayable(): SinglePlayableQueueFragment {
        return this
    }
}

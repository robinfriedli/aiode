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

    private var played: Boolean = false

    override fun currentIndex(): Int {
        return if (played) {
            0
        } else {
            -1
        }
    }

    override fun next(): Playable {
        if (played) {
            throw IllegalStateException("${javaClass.simpleName} has no next element")
        }

        played = true
        return playable
    }

    override fun current(): Playable {
        return playable
    }

    override fun hasNext(fractureIdx: Int): Boolean {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            !played
        }
    }

    override fun hasPrevious(fractureIdx: Int): Boolean {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            played
        }
    }

    override fun previous(): Playable {
        if (!played) {
            throw IllegalStateException("${javaClass.simpleName} has no previous element")
        }

        played = false
        return playable
    }

    override fun peekNext(): Playable? {
        return if (played) {
            null
        } else {
            playable
        }
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
            if (played) {
                Collections.emptyList()
            } else {
                getPlayables()
            }
        }
    }

    override fun getPreviousPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable> {
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else {
            if (!played) {
                Collections.emptyList()
            } else {
                getPlayables()
            }
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
        return if (fractureIdx > 0) {
            throw IndexOutOfBoundsException("Fracture index $fractureIdx out of bounds for ${javaClass.simpleName}")
        } else if (offset > 0) {
            throw IndexOutOfBoundsException("Offset $offset out of bounds for ${javaClass.simpleName}")
        } else {
            played = true
        }
    }

    override fun resetPositionToStart() {
        played = false
    }

    override fun resetPositionToEnd() {
        played = true
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

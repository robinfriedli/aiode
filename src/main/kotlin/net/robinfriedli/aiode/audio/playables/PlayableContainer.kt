package net.robinfriedli.aiode.audio.playables

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.queue.AudioQueue
import net.robinfriedli.aiode.audio.queue.QueueFragment

interface PlayableContainer<T> {

    fun getItem(): T

    fun loadPlayables(playableFactory: PlayableFactory): List<Playable>

    /**
     * Load a single Playable for this container. Only available for [AbstractSinglePlayableContainer] implementations, i.e.
     * containers that resolve to a single playable. Else this method always throws [UnsupportedOperationException].
     * See [isSinglePlayable] to check whether the method is available for any given implementation of this interface.
     */
    @Throws(UnsupportedOperationException::class)
    fun loadPlayable(playableFactory: PlayableFactory): Playable? {
        throw UnsupportedOperationException("PlayableContainer $this does not support resolving to a single Playable")
    }

    /**
     * Create a [QueueFragment] for this PlayableContainer, may return `null` if [loadPlayables] does not return any results.
     */
    fun createQueueFragment(playableFactory: PlayableFactory, queue: AudioQueue): QueueFragment?

    fun isSinglePlayable(): Boolean {
        return false
    }

}

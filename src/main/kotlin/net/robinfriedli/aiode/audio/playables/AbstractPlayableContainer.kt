package net.robinfriedli.aiode.audio.playables

import com.google.common.collect.Lists
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.queue.AudioQueue
import net.robinfriedli.aiode.audio.queue.PlayableContainerQueueFragment
import net.robinfriedli.aiode.audio.queue.QueueFragment

/**
 * Abstract class for anything that represents a [Playable] or contains [Playable] instances, e.g. Spotify tracks, playlists
 * albums etc.
 */
abstract class AbstractPlayableContainer<T>(private val item: T) : PlayableContainer<T> {

    var playables: List<Playable>? = null

    final override fun loadPlayables(playableFactory: PlayableFactory): List<Playable> {
        if (playables == null) {
            playables = doLoadPlayables(playableFactory)
        }

        return playables!!
    }

    override fun createQueueFragment(playableFactory: PlayableFactory, queue: AudioQueue): QueueFragment? {
        val playables = loadPlayables(playableFactory)
        return if (playables.isNotEmpty()) {
            PlayableContainerQueueFragment(queue, Lists.newArrayList(playables), this)
        } else {
            null
        }
    }

    abstract fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable>

    override fun getItem(): T {
        return item
    }

}

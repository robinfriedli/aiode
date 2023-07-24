package net.robinfriedli.aiode.audio.queue

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer

/**
 * Represents a structure that may be added to the AudioQueue. Can either be single tracks, playlists, albums etc.
 * Implementations do not have to be thread safe since queue access is synchronised.
 */
interface QueueFragment {

    /**
     * Return the number of [Playable] instances in this fragment. The size should never be less than 1, if the last element
     * is removed from a fragment, the fragment gets removed from the queue entirely.
     */
    fun size(): Int

    /**
     * Return the [PlayableContainer] this QueueFragment is based on.
     */
    fun getPlayableContainer(): PlayableContainer<*>

    /**
     * Return the [AudioQueue] this fragment belongs to.
     */
    fun getQueue(): AudioQueue

    /**
     * Fracture this fragment at the given index. Fractures mean that a different fragment has been inserted at an index
     * that lies within this fragment. In this case a new node for this fragment will be created after the inserted node
     * and a fracture will be added for the index. The first [hasNext] call when reaching the fracture will return false,
     * the next call will return true. For example if an AudioQueue contains a single fragment with 10 tracks and a new
     * fragment is inserted at index 7, the queue will look like this:
     *
     * QueueNode(fragment1(0 - 6) { fractures: { 7 } }) | QueueNode(fragment2) | QueueNode(fragment1(7 - 9) { fractures: { 7 } })
     *
     * @return the index of the created fracture for [getPlayablesInFracture]
     */
    fun addFracture(idx: Int): Int

    /**
     * Fracture the fragment at the given index, relative to the start of the given fracture.
     */
    fun addFracture(fractureIdx: Int, idx: Int): Int

    /**
     * @return all Playables in this fragment
     */
    fun getPlayables(): List<Playable>

    /**
     * @return the Playable at the given index within the given fracture
     */
    fun getPlayableInFracture(fractureIdx: Int, idx: Int): Playable

    /**
     * @return the Playables within the given fracture, each fragment at least has one fracture at index 0
     */
    fun getPlayablesInFracture(fractureIdx: Int): List<Playable>

    fun sizeOfFracture(fractureIdx: Int): Int

}

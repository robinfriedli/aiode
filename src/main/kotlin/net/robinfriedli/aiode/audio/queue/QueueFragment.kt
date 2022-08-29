package net.robinfriedli.aiode.audio.queue

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer

/**
 * Represents a structure that may be added to the AudioQueue. Can either be single tracks, playlists, albums etc.
 * Implementations do not have to be thread safe since queue access is synchronised.
 */
interface QueueFragment {

    /**
     * Get the index within this fragment of the current track, returns -1 if this fragment is not currently playing.
     */
    fun currentIndex(): Int

    /**
     * Get the next element to play from this fragment, throws if [hasNext] returns false.
     */
    fun next(): Playable

    /**
     * Require the current track or throw
     */
    fun current(): Playable

    /**
     * Return `true` if there is a next element in the given fracture of this fragment.
     */
    fun hasNext(fractureIdx: Int): Boolean

    /**
     * Return `true` if there is a previous element in the given fracture of this fragment.
     */
    fun hasPrevious(fractureIdx: Int): Boolean

    fun previous(): Playable

    fun peekNext(): Playable?

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

    fun getOrderedFractures(): List<Int>

    /**
     * @return all Playables in this fragment
     */
    fun getPlayables(): List<Playable>

    /**
     * @return all Playables in this fragment in the current queue order, i.e. the shuffled order if shuffle is enabled
     */
    fun getPlayablesInCurrentOrder(): List<Playable>

    /**
     * @return the Playables within the given fracture, each fragment at least has one fracture at index 0
     */
    fun getPlayablesInFracture(fractureIdx: Int): List<Playable>

    /**
     * Return maximum [limit] tracks from the provided fracture, if the current position lies within the given fracture
     * this only returns tracks after the current track.
     */
    fun getNextPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable>

    /**
     * Return maximum [limit] tracks from the provided fracture, if the current position lies within the given fracture
     * this only returns tracks before the current track.
     */
    fun getPreviousPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable>

    fun sizeOfFracture(fractureIdx: Int): Int

    /**
     * @return the index relative to the start of the given fracture, -1 if the given fracture is not currently playing
     */
    fun currentIndexWithinFracture(fractureIdx: Int): Int

    /**
     * Set the position to the track within the given fracture and offset within that fracture.
     */
    fun setPosition(fractureIdx: Int, offset: Int)

    fun resetPositionToStart()
    fun resetPositionToEnd()

    /**
     * Shuffles this fragment. If this fragment contains several playables, it may create random fractures to enable other
     * fragments to be placed at indices within this fragment, otherwise all tracks of the same fragment would remain
     * grouped together when enabling shuffle.
     *
     * @param protectCurrent whether to make sure to keep the current item at the same index when currently playing
     * @return the number of random fractures created
     */
    fun enableShuffle(protectCurrent: Boolean): Int

    fun disableShuffle()

    /**
     * Return a queue fragment representing the current element. May throw if this fragment isn't the current fragment.
     */
    fun reduceToCurrentPlayable(): SinglePlayableQueueFragment

}

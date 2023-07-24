package net.robinfriedli.aiode.audio.queue

import com.google.common.collect.Lists
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import java.util.stream.IntStream

class PlayableContainerQueueFragment(
    private val queue: AudioQueue,
    private val playables: MutableList<Playable>,
    private val playableContainer: PlayableContainer<*>
) : QueueFragment {

    private var fractures: MutableList<Pair<Int, Int>> = Lists.newArrayList(Pair(0, size()))

    override fun size(): Int {
        return playables.size
    }

    override fun getPlayableContainer(): PlayableContainer<*> {
        return playableContainer
    }

    override fun getQueue(): AudioQueue {
        return queue
    }

    override fun addFracture(idx: Int): Int {
        if (idx <= 0 || idx >= size()) {
            throw IndexOutOfBoundsException("Cannot create fracture at index $idx")
        }

        val (parentFractureIdx, parentFracture) = IntStream
            .range(0, fractures.size)
            .mapToObj { i -> Pair(i, fractures[i]) }
            .sorted(Comparator.comparing { (_, fracture) -> fracture.first })
            .filter { (_, fracture) -> fracture.first <= idx && fracture.second > idx }
            .findFirst()
            .orElseThrow { IllegalStateException("Could not find suitable parent fracture for index $idx") }

        if (parentFracture.first == idx) {
            throw IllegalStateException("Fragment is already fractured at $idx")
        }

        fractures[parentFractureIdx] = Pair(parentFracture.first, idx)
        fractures.add(Pair(idx, parentFracture.second))
        return fractures.size - 1
    }

    override fun addFracture(fractureIdx: Int, idx: Int): Int {
        return addFracture(fractures[fractureIdx].first + idx)
    }

    override fun getPlayables(): List<Playable> {
        return playables
    }

    override fun getPlayableInFracture(fractureIdx: Int, idx: Int): Playable {
        val (start, end) = fractures[fractureIdx]
        val absoluteIdx = start + idx

        if (absoluteIdx >= end) {
            throw IndexOutOfBoundsException("Index $idx out of bounds for fracture of size ${end - start}")
        }

        return playables[absoluteIdx]
    }

    override fun getPlayablesInFracture(fractureIdx: Int): List<Playable> {
        val (start, end) = fractures[fractureIdx]
        return ArrayList(playables).subList(start, end)
    }

    override fun sizeOfFracture(fractureIdx: Int): Int {
        val (start, end) = fractures[fractureIdx]
        return end - start
    }
}

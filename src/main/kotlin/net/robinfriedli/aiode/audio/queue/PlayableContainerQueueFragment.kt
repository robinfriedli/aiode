package net.robinfriedli.aiode.audio.queue

import com.google.common.collect.Sets
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.containers.SinglePlayableContainer
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream

class PlayableContainerQueueFragment(
    private val queue: AudioQueue,
    private val playables: MutableList<Playable>,
    private val playableContainer: PlayableContainer<*>
) : QueueFragment {

    private var currentIndex: Int = -1
    private var randomOrder: MutableList<Int>? = null

    private var fractures: MutableSet<Int> = Sets.newHashSet()
    private var altFractures: MutableSet<Int>? = null

    override fun currentIndex(): Int {
        return currentIndex
    }

    override fun next(): Playable {
        return if (randomOrder != null) {
            playables[randomOrder!![++currentIndex]]
        } else {
            playables[++currentIndex]
        }
    }

    override fun current(): Playable {
        return if (randomOrder != null) {
            playables[randomOrder!![currentIndex]]
        } else {
            playables[currentIndex]
        }
    }

    override fun hasNext(fractureIdx: Int): Boolean {
        return if (currentIndex < size() - 1) {
            val (_, end) = getFractureRange(fractureIdx)
            currentIndex < end - 1
        } else {
            false
        }
    }

    override fun hasPrevious(fractureIdx: Int): Boolean {
        return if (currentIndex > 0) {
            val (start, _) = getFractureRange(fractureIdx)
            currentIndex > start
        } else {
            false
        }
    }

    override fun previous(): Playable {
        return if (randomOrder != null) {
            playables[randomOrder!![--currentIndex]]
        } else {
            playables[--currentIndex]
        }
    }

    override fun peekNext(): Playable? {
        return if (currentIndex < size() - 1) {
            if (randomOrder != null) {
                playables[randomOrder!![currentIndex + 1]]
            } else {
                playables[currentIndex + 1]
            }
        } else {
            null
        }
    }

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
        fractures.add(idx)
        return fractures.size
    }

    override fun getOrderedFractures(): List<Int> {
        return fractures.stream().sorted().toList()
    }

    override fun getPlayables(): List<Playable> {
        return playables
    }

    override fun getPlayablesInCurrentOrder(): List<Playable> {
        return if (randomOrder != null) {
            randomOrder!!
                .stream()
                .map { idx -> playables[idx] }
                .toList()
        } else {
            getPlayables()
        }
    }

    override fun getPlayableInFracture(fractureIdx: Int, idx: Int): Playable {
        val (start, end) = getFractureRange(fractureIdx)
        val absoluteIdx = start + idx

        if (absoluteIdx >= end) {
            throw IndexOutOfBoundsException("Index $idx out of bounds for fracture of size ${end - start}")
        }

        return getPlayablesInCurrentOrder()[absoluteIdx]
    }

    override fun getPlayablesInFracture(fractureIdx: Int): List<Playable> {
        val (start, end) = getFractureRange(fractureIdx)
        return getPlayablesInCurrentOrder().subList(start, end)
    }

    override fun getNextPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable> {
        val (start, end) = getFractureRange(fractureIdx)
        val nextStart = currentIndex + 1

        return if (currentIndex in start until end) {
            getPlayablesInCurrentOrder().subList(nextStart, (nextStart + limit).coerceAtMost(end))
        } else {
            getPlayablesInCurrentOrder().subList(start, (start + limit).coerceAtMost(end))
        }
    }

    override fun getPreviousPlayablesInFracture(fractureIdx: Int, limit: Int): List<Playable> {
        val (start, end) = getFractureRange(fractureIdx)
        val prevEnd = currentIndex

        return if (prevEnd in start until end) {
            getPlayablesInCurrentOrder().subList((prevEnd - limit).coerceAtLeast(start), prevEnd)
        } else {
            getPlayablesInCurrentOrder().subList((end - limit).coerceAtLeast(start), end)
        }
    }

    override fun sizeOfFracture(fractureIdx: Int): Int {
        val (start, end) = getFractureRange(fractureIdx)
        return end - start
    }

    override fun currentIndexWithinFracture(fractureIdx: Int): Int {
        val (start, end) = getFractureRange(fractureIdx)

        return if (currentIndex in start until end) {
            currentIndex - start
        } else {
            -1
        }
    }

    override fun setPosition(fractureIdx: Int, offset: Int) {
        val (start, _) = getFractureRange(fractureIdx)
        currentIndex = start + offset
    }

    override fun resetPositionToStart() {
        currentIndex = -1
    }

    override fun resetPositionToEnd() {
        currentIndex = size()
    }

    override fun enableShuffle(protectCurrent: Boolean): Int {
        val size = size()
        val range = IntStream.range(0, size).boxed().collect(Collectors.toList())
        range.shuffle()
        randomOrder = range

        if (protectCurrent && currentIndex >= 0) {
            // make sure the current track is at index 0 / at the start of randomOrder
            if (randomOrder!![0] != currentIndex) {
                val indexOfCurrent = randomOrder!!.indexOf(currentIndex)
                val prev = randomOrder!!.set(0, currentIndex)
                randomOrder!![indexOfCurrent] = prev
            }

            currentIndex = 0
        }

        val randomFractureCount = Random().nextInt(size)

        val random = Random()
        altFractures = fractures
        fractures = Sets.newHashSet()
        for (i in 0 until randomFractureCount) {
            // values returned by the same generator are likely unique, occasional duplicate values are not a problem,
            // just means fewer fractures than randomFractureCount
            fractures.add((random.nextInt(size) + 1).coerceAtMost(size - 1))
        }

        return fractures.size
    }

    override fun disableShuffle() {
        if (currentIndex >= 0 && randomOrder != null) {
            currentIndex = randomOrder!![currentIndex]
        }
        randomOrder = null
        if (altFractures != null) {
            fractures = altFractures!!
        }
        altFractures = null
    }

    override fun reduceToCurrentPlayable(): SinglePlayableQueueFragment {
        return if (randomOrder != null) {
            SinglePlayableQueueFragment(queue, playables[randomOrder!![currentIndex]], SinglePlayableContainer(playables[randomOrder!![currentIndex]]))
        } else {
            SinglePlayableQueueFragment(queue, playables[currentIndex], SinglePlayableContainer(playables[currentIndex]))
        }
    }

    private fun getFractureRange(fractureIdx: Int): Pair<Int, Int> {
        val fractures = getOrderedFractures()
        // there always is one fracture at idx 0
        return if (fractureIdx == 0) {
            if (fractures.isEmpty()) {
                Pair(0, playables.size)
            } else {
                Pair(0, fractures[0])
            }
        } else if (fractureIdx > 0 && fractureIdx < fractures.size) {
            Pair(fractures[fractureIdx - 1], fractures[fractureIdx])
        } else if (fractureIdx > 0 && fractureIdx == fractures.size) {
            Pair(fractures[fractureIdx - 1], playables.size)
        } else {
            throw IndexOutOfBoundsException("Index $fractureIdx out of bounds for fragment with ${fractures.size + 1} fractures")
        }
    }
}

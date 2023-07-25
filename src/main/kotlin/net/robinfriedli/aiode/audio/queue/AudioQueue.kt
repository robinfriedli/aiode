package net.robinfriedli.aiode.audio.queue

import com.google.common.collect.Lists
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.robinfriedli.aiode.Aiode
import net.robinfriedli.aiode.audio.AudioPlayback
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import net.robinfriedli.aiode.audio.playables.containers.SinglePlayableContainer
import net.robinfriedli.aiode.discord.property.properties.ColorSchemeProperty
import net.robinfriedli.aiode.entities.GuildSpecification
import net.robinfriedli.aiode.exceptions.NoResultsFoundException
import net.robinfriedli.aiode.exceptions.UserException
import net.robinfriedli.aiode.persist.StaticSessionProvider
import net.robinfriedli.aiode.util.EmojiConstants
import net.robinfriedli.aiode.util.Util
import org.hibernate.Session
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.streams.toList


class AudioQueue(val maxSize: Int?) {

    @Volatile
    var isShuffle: Boolean = false
        set(value) {
            val writeLock = lock.writeLock()
            writeLock.lock()
            try {
                if (!isEmpty()) {
                    if (value) {
                        doRandomize(true)
                    } else if (isShuffle) {
                        disableShuffle()
                    }
                }
                field = value
            } finally {
                writeLock.unlock()
            }
        }

    @Volatile
    var repeatOne: Boolean = false

    @Volatile
    var repeatAll: Boolean = false

    @Volatile
    var currIdx: Int = -1

    @Volatile
    var size: Int = 0

    private val nodeList: QueueNodeList = QueueNodeList()
    private val shuffledOrder = ArrayList<Int>()

    @Volatile
    private var flattenedQueue: List<Playable>? = null

    val lock: ReadWriteLock = ReentrantReadWriteLock()

    fun getTracks(): List<Playable> {
        val readLock = lock.readLock()
        readLock.lock()
        try {
            val trackList: MutableList<Playable> = ArrayList(size)
            if (isShuffle) {
                for (pos in shuffledOrder) {
                    trackList.add(getPlayableAtIndexLocked(pos))
                }
            } else {
                val flattenedQueue = flattenedQueue
                if (flattenedQueue != null) {
                    return flattenedQueue
                }
                for (node in nodeList) {
                    trackList.addAll(node.getPlayables())
                }
            }

            return trackList
        } finally {
            readLock.unlock()
        }
    }

    fun getPosition(): Int {
        return currIdx
    }

    fun setPosition(idx: Int) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            currIdx = idx
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Require the track at the current position or throw if empty / never iterated
     */
    fun getCurrent(): Playable {
        val readLock = lock.readLock()
        readLock.lock()
        return try {
            getCurrentLocked()
        } finally {
            readLock.unlock()
        }
    }

    fun getCurrentLocked(): Playable {
        return if (isShuffle) {
            getPlayableAtIndexLocked(shuffledOrder[currIdx])
        } else {
            getPlayableAtIndexLocked(currIdx)
        }
    }

    fun getPlayableAtIndex(idx: Int): Playable {
        val readLock = lock.readLock()
        readLock.lock()
        return try {
            getPlayableAtIndexLocked(idx)
        } finally {
            readLock.unlock()
        }
    }

    private fun getPlayableAtIndexLocked(idx: Int): Playable {
        val flattenedQueue = this.flattenedQueue
        return if (flattenedQueue != null) {
            flattenedQueue[idx]
        } else {
            nodeList.getPlayableAtIndex(idx)
        }
    }

    fun iterate() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            iterateLocked()
        } finally {
            writeLock.unlock()
        }
    }

    fun iterateLocked() {
        currIdx = nextPosition()
    }

    fun reverse() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            currIdx = previousPosition()
        } finally {
            writeLock.unlock()
        }
    }

    fun peekNext(): Playable? {
        val readLock = lock.readLock()
        readLock.lock()
        try {
            return if (isShuffle && currIdx < shuffledOrder.size - 1) {
                getPlayableAtIndexLocked(shuffledOrder[currIdx + 1])
            } else if (!isShuffle && currIdx < size - 1) {
                getPlayableAtIndexLocked(currIdx + 1)
            } else {
                null
            }
        } finally {
            readLock.unlock()
        }
    }

    fun hasNext(): Boolean {
        return hasNext(false)
    }

    fun hasNext(ignoreRepeat: Boolean): Boolean {
        if (isEmpty()) {
            return false
        }

        val inBound = currIdx < size - 1
        return if (ignoreRepeat) {
            inBound
        } else {
            inBound || repeatOne || repeatAll
        }
    }

    fun hasPrevious(): Boolean {
        return hasPrevious(false)
    }

    fun hasPrevious(ignoreRepeat: Boolean): Boolean {
        if (isEmpty()) {
            return false
        }

        val inBound = currIdx > 0
        return if (ignoreRepeat) {
            inBound
        } else {
            inBound || repeatOne || repeatAll
        }
    }

    fun isEmpty(): Boolean {
        return size == 0
    }

    fun clear() {
        clear(false)
    }

    fun clear(retainCurrent: Boolean) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doClear(retainCurrent)
        } finally {
            writeLock.unlock()
        }
    }

    fun add(fragment: QueueFragment) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doInsert(size, fragment)
        } finally {
            writeLock.unlock()
        }
    }

    fun insert(idx: Int, fragment: QueueFragment) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doInsert(idx, fragment)
        } finally {
            writeLock.unlock()
        }
    }

    fun insertNext(fragment: QueueFragment) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doInsert(currIdx + 1, fragment)
        } finally {
            writeLock.unlock()
        }
    }

    fun insertRelative(relativeIdx: Int, fragment: QueueFragment) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doInsert(currIdx + relativeIdx + 1, fragment)
        } finally {
            writeLock.unlock()
        }
    }

    fun set(fragment: QueueFragment) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doClear(false)
            doInsert(size, fragment)
        } finally {
            writeLock.unlock()
        }
    }

    fun addContainers(containers: List<PlayableContainer<*>>, playableFactory: PlayableFactory, clear: Boolean, insertionIdx: Int? = null): Int {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            return addContainersLocked(containers, playableFactory, clear, insertionIdx)
        } finally {
            writeLock.unlock()
        }
    }

    fun addContainersLocked(containers: List<PlayableContainer<*>>, playableFactory: PlayableFactory, clear: Boolean, insertionIdx: Int? = null): Int {
        val queueFragments: MutableList<QueueFragment> = ArrayList()
        for (playableContainer in containers) {
            val queueFragment = playableContainer.createQueueFragment(playableFactory, this)
            if (queueFragment != null) {
                queueFragments.add(queueFragment)
            }
        }

        var loadedAmount = 0
        if (queueFragments.isNotEmpty()) {
            if (clear) {
                doClear(false)
            }
            for (queueFragment in queueFragments) {
                doInsert(if (insertionIdx != null) {
                    currIdx + insertionIdx + 1
                } else {
                    size
                }, queueFragment)
                loadedAmount += queueFragment.size()
            }
        } else {
            throw NoResultsFoundException("No results found")
        }

        return loadedAmount
    }

    fun remove(fromIdx: Int, toIdx: Int): Int {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            if (toIdx <= fromIdx) {
                throw IllegalArgumentException("Invalid range $fromIdx - $toIdx")
            } else if (fromIdx < 0 || toIdx > size) {
                throw IndexOutOfBoundsException("Range $fromIdx - $toIdx out of bounds for size $size")
            }
            return doRemove(fromIdx, toIdx)
        } finally {
            writeLock.unlock()
        }
    }

    fun removeRelative(fromIdx: Int, toIdx: Int): Int {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            val relativeStart = currIdx + 1
            val from = relativeStart + fromIdx
            val to = relativeStart + toIdx
            if (to <= from) {
                throw IllegalArgumentException("Invalid range $from - $to")
            } else if (from < 0 || to > size) {
                throw IndexOutOfBoundsException("Range $from - $to out of bounds for size $size")
            }
            return doRemove(from, to)
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Format the current queue as a Discord embed message showing all enabled options, such as shuffle and repeat,
     * the volume and previous 5, the current and the next 5 tracks and also provides a link to view the full queue
     *
     * @return the [EmbedBuilder] to build send to Discord with the colour specified by the [GuildSpecification]
     * already applied
     */
    fun buildMessageEmbed(playback: AudioPlayback, guild: Guild): EmbedBuilder {
        val readLock = lock.readLock()
        readLock.lock()
        try {
            val position = getPosition()
            val embedBuilder = EmbedBuilder()
            val springPropertiesConfig = Aiode.get().springPropertiesConfig
            val baseUri = springPropertiesConfig.requireApplicationProperty("aiode.server.base_uri")
            val optionBuilder = StringBuilder()

            appendIcon(optionBuilder, EmojiConstants.PLAY, playback.isPlaying)
            appendIcon(optionBuilder, EmojiConstants.PAUSE, playback.isPaused)
            appendIcon(optionBuilder, EmojiConstants.SHUFFLE, playback.isShuffle)
            appendIcon(optionBuilder, EmojiConstants.REPEAT, playback.isRepeatAll)
            appendIcon(optionBuilder, EmojiConstants.REPEAT_ONE, playback.isRepeatOne)
            optionBuilder.append(EmojiConstants.VOLUME).append(playback.volume)
            embedBuilder.setDescription(optionBuilder.toString())

            val url = baseUri + String.format("/queue?guildId=%s", guild.id)
            embedBuilder.addField("", "[Full list]($url)", false)

            if (isEmpty()) {
                embedBuilder.addField("", "(empty)", false)
            } else {
                val prevBuilder = StringBuilder()
                val nextBuilder = StringBuilder()
                if (position > 0) {
                    if (position > 5) {
                        prevBuilder.append("...").append(System.lineSeparator())
                    }
                    val previous: List<Playable> = listPrevLocked(5)
                    for (prev in previous) {
                        appendPlayable(prevBuilder, prev)
                    }
                }
                if (prevBuilder.toString().isNotEmpty()) {
                    embedBuilder.addField("Previous", prevBuilder.toString(), false)
                }
                val currentPosition = Util.normalizeMillis(playback.currentPositionMs)
                val current = getCurrentLocked()
                val duration = Util.normalizeMillis(current.durationMs())
                embedBuilder.addField(
                    "Current",
                    "| " + current.displayNow + " - " + currentPosition + " / " + duration,
                    false
                )
                if (position < size - 1) {
                    val next: List<Playable> = listNextLocked(5)
                    for (n in next) {
                        appendPlayable(nextBuilder, n)
                    }
                    if (size > position + 6) {
                        nextBuilder.append("...")
                    }
                }
                if (nextBuilder.toString().isNotEmpty()) {
                    embedBuilder.addField("Next", nextBuilder.toString(), false)
                }
                val albumCoverUrl = current.albumCoverUrl
                embedBuilder.setThumbnail(Objects.requireNonNullElseGet(albumCoverUrl) { "$baseUri/resources-public/img/aiode-logo.png" })
            }
            val color = StaticSessionProvider.invokeWithSession { session: Session? ->
                val specification = Aiode.get().guildManager.getContextForGuild(guild).getSpecification(session)
                ColorSchemeProperty.getColor(specification)
            }
            embedBuilder.setColor(color)
            return embedBuilder
        } finally {
            readLock.unlock()
        }
    }

    fun listNext(limit: Int): List<Playable> {
        val readLock = lock.readLock()
        readLock.lock()
        return try {
            listNextLocked(limit)
        } finally {
            readLock.unlock()
        }
    }

    fun listNextLocked(limit: Int): List<Playable> {
        val trackList: MutableList<Playable> = ArrayList(limit)
        var idx = currIdx

        while (trackList.size < limit && (repeatAll || idx < size - 1)) {
            if (idx < size - 1) {
                idx += 1
            } else {
                idx = 0
            }

            if (isShuffle) {
                trackList.add(getPlayableAtIndexLocked(shuffledOrder[idx]))
            } else {
                trackList.add(getPlayableAtIndexLocked(idx))
            }
        }

        return trackList
    }

    fun listPrev(limit: Int): List<Playable> {
        val readLock = lock.readLock()
        readLock.lock()
        return try {
            listPrevLocked(limit)
        } finally {
            readLock.unlock()
        }
    }

    fun listPrevLocked(limit: Int): List<Playable> {
        // use linked list as items are always inserted at head
        val trackList: MutableList<Playable> = LinkedList()
        var idx = currIdx

        while (trackList.size < limit && idx > 0) {
            idx -= 1

            if (isShuffle) {
                trackList.add(0, getPlayableAtIndexLocked(shuffledOrder[idx]))
            } else {
                trackList.add(0, getPlayableAtIndexLocked(idx))
            }
        }

        return trackList
    }

    fun reset() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            if (!isEmpty()) {
                iterateLocked()
            } else {
                currIdx = -1
            }
        } finally {
            writeLock.unlock()
        }
    }

    fun randomize() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doRandomize(true)
        } finally {
            writeLock.unlock()
        }
    }

    private fun doRandomize(protectCurrent: Boolean) {
        shuffledOrder.clear()
        val indices: MutableList<Int> = IntStream.range(0, size).boxed().collect(Collectors.toList())
        val retainedIdx = if (protectCurrent && currIdx >= 0) {
            indices.removeAt(currIdx)
        } else {
            null
        }
        indices.shuffle()
        if (retainedIdx != null) {
            indices.add(0, retainedIdx)
        }
        shuffledOrder.addAll(indices)
        currIdx = if (!isEmpty()) {
            0
        } else {
            -1
        }
    }

    private fun disableShuffle() {
        // when setting the queue from shuffle back to normal the current track index has to be adjusted since
        // currentTrack acts as cursor rather than actual queue position in shuffle mode
        currIdx = shuffledOrder[currIdx];
        shuffledOrder.clear()
    }

    private fun doInsert(idx: Int, fragment: QueueFragment) {
        if (isShuffle) {
            nodeList.insert(size, QueueNode(fragment, 0), false)
            val random = Random()
            val end = size + fragment.size()
            val indices = IntStream.range(size, end).boxed().collect(Collectors.toCollection { ArrayList() })

            if (idx == size) {
                indices.shuffle()
                // when appending items to the end of the queue, randomise them by inserting them at random indices after the current position
                for (index in indices) {
                    val insertionIndex = random.nextInt(currIdx + 1, end)
                    if (insertionIndex < shuffledOrder.size) {
                        shuffledOrder.add(insertionIndex, index)
                    } else {
                        shuffledOrder.add(index)
                    }
                }
            } else {
                // when inserting tracks into the queue at a specific position, keep them in order
                shuffledOrder.addAll(idx, indices)
            }
        } else {
            nodeList.insert(idx, QueueNode(fragment, 0))
        }

        rebuildFlattenedQueue()

        if (size == 0) {
            size += fragment.size()
            iterate()
        } else {
            size += fragment.size()
        }
    }

    private fun doRemove(fromIdx: Int, toIdx: Int): Int {
        return if (isShuffle) {
            // instead of removing each index one by one gather adjacent indices and remove each range
            val indices = IntStream.range(fromIdx, toIdx).map { i -> shuffledOrder[i] }.sorted().toList()
            if (indices.isEmpty()) {
                return 0
            }
            shuffledOrder.removeIf { i -> indices.contains(i) }
            val indexRanges = Lists.newArrayList<Pair<Int, Int>>()
            var currStart: Int? = null
            for (i in 1 until indices.size) {
                val curr = indices[i]
                val prev = indices[i - 1]
                if (curr > prev + 1) {
                    indexRanges.add(Pair(currStart ?: indices[0], prev + 1))
                    currStart = curr
                }
            }
            indexRanges.add(Pair(currStart ?: indices[0], indices.last() + 1))

            var removed = 0
            // remove highest indexes first to avoid removal of lower index invalidating higher indexes
            for ((from, to) in indexRanges.reversed()) {
                val removedRange = nodeList.remove(from, to)
                for (i in 0 until shuffledOrder.size) {
                    if (shuffledOrder[i] > from) {
                        shuffledOrder[i] = shuffledOrder[i] - removedRange
                    }
                }
                removed += removedRange
            }

            rebuildFlattenedQueue()
            removed
        } else {
            val removed = nodeList.remove(fromIdx, toIdx)
            rebuildFlattenedQueue()
            removed
        }
    }

    private fun doClear(retainCurrent: Boolean) {
        if (retainCurrent && currIdx >= 0) {
            val current = getCurrentLocked()
            nodeList.clear(SinglePlayableQueueFragment(this, current, SinglePlayableContainer(current)))
            size = 1
            currIdx = 0
            doRandomize(true)
            rebuildFlattenedQueue()
        } else {
            nodeList.clear()
            flattenedQueue = null
            shuffledOrder.clear()
            size = 0
            currIdx = -1
        }
    }

    private fun nextPosition(): Int {
        return if (currIdx < size - 1) {
            currIdx + 1
        } else {
            0
        }
    }

    private fun previousPosition(): Int {
        return if (currIdx > 0) {
            currIdx - 1
        } else {
            if (repeatAll) getTracks().size - 1 else 0
        }
    }

    private fun appendIcon(builder: StringBuilder, unicode: String, enabled: Boolean) {
        if (enabled) {
            builder.append(unicode).append(" ")
        }
    }

    private fun appendPlayable(trackListBuilder: StringBuilder, playable: Playable) {
        playable.fetch()
        var display = playable.display(100, TimeUnit.MILLISECONDS)
        val durationMs = playable.durationMs(100, TimeUnit.MILLISECONDS)
        if (display.length > 100) {
            display = display.substring(0, 95) + "[...]"
        }
        trackListBuilder.append("| ").append(display).append(" - ").append(Util.normalizeMillis(durationMs)).append(System.lineSeparator())
    }

    private fun rebuildFlattenedQueue() {
        val flattenedQueue: MutableList<Playable> = ArrayList(size)
        for (queueNode in nodeList) {
            flattenedQueue.addAll(queueNode.getPlayables())
        }
        this.flattenedQueue = flattenedQueue
    }

    private inner class QueueNodeList : Iterable<QueueNode> {
        @Volatile
        var head: QueueNode? = null

        @Volatile
        var tail: QueueNode? = null

        override fun iterator(): Iterator<QueueNode> {
            return object : Iterator<QueueNode> {

                var next = head

                override fun hasNext(): Boolean {
                    return next != null
                }

                override fun next(): QueueNode {
                    val ret = next!!
                    next = ret.next
                    return ret
                }

            }
        }

        fun clear(retainCurrent: QueueFragment? = null) {
            if (retainCurrent != null) {
                val node = QueueNode(retainCurrent, 0)
                head = node
                tail = node
            } else {
                head = null
                tail = null
            }
        }

        fun insert(idx: Int, node: QueueNode, updateQueueIndex: Boolean = true) {
            if (idx > size || idx < 0) {
                throw IndexOutOfBoundsException("Index $idx out of bounds for queue of size $size")
            }

            if (maxSize != null && size + node.fragment.size() > maxSize) {
                throw UserException("Queue exceeds maximum size of $maxSize tracks")
            }

            if (head == null) {
                head = node
                tail = node
            } else if (idx == 0) {
                head!!.prev = node
                node.next = head!!
                head = node
            } else if (idx == size) {
                tail!!.next = node
                node.prev = tail!!
                tail = node
            } else {
                val (offset, foundNode) = getNodeAtIndex(idx)
                if (offset == 0) {
                    // index points to first item in node, can just insert new node ahead of the found node
                    linkBefore(node, foundNode)
                } else {
                    // new node is inserted within the found node, fracture the found node and place new node between the two fractures
                    val fractureIdx = foundNode.fracture(offset)
                    val fracturedNode = QueueNode(foundNode.fragment, fractureIdx)
                    linkAfter(node, foundNode)
                    linkAfter(fracturedNode, node)
                }
            }

            if (updateQueueIndex && idx <= currIdx) {
                currIdx += node.size()
            }
        }

        fun remove(fromIdx: Int, toIdx: Int): Int {
            if (fromIdx < 0) {
                throw IndexOutOfBoundsException("Index $fromIdx out of bounds for queue of size $size")
            }
            if (toIdx > size) {
                throw IndexOutOfBoundsException("Index $toIdx out of bounds for queue of size $size")
            }
            if (fromIdx >= toIdx) {
                throw IllegalArgumentException("toIdx must be greater than fromIdx")
            }

            var toRemove = toIdx - fromIdx
            var (offset, node) = getNodeAtIndex(fromIdx)
            do {
                val next = node.next
                if (offset > 0) {
                    val fractureIdx = node.fracture(offset)
                    val fracturedNode = QueueNode(node.fragment, fractureIdx)
                    val fracturedSize = fracturedNode.size()
                    if (fracturedSize > toRemove) {
                        val nextNodeFracture = node.fracture(offset + toRemove)
                        // e.g. given remove index 3 - 8 (excluding) from node:
                        // 0 1 2 | 3 4 5 6 7 | 8 9
                        // create fracture at 3 and 8
                        // dismiss the first fracture that points to the range of removed items
                        // insert second fracture after the original node
                        // creates the following state: {QueueNode(fracture 0: [0, 1, 2]), (unlinked) QueueNode(fracture 1: [3, 4, 5, 6, 7]), QueueNode(fracture 2: [8, 9])}
                        linkAfter(QueueNode(node.fragment, nextNodeFracture), node)
                        size -= fracturedNode.size()
                        toRemove = 0
                    } else {
                        toRemove -= fracturedSize
                        size -= fracturedSize
                    }
                } else if (toRemove >= node.size()) {
                    toRemove -= node.size()
                    removeNode(node)
                } else {
                    val nextFracture = node.fracture(toRemove)
                    val nextNode = QueueNode(node.fragment, nextFracture)
                    val prevNode = node.prev
                    toRemove -= node.size()
                    removeNode(node)
                    if (prevNode != null) {
                        linkAfter(nextNode, prevNode)
                    } else {
                        val currHead = head
                        nextNode.next = currHead
                        head = nextNode
                        if (currHead == null) {
                            tail = nextNode
                        } else {
                            currHead.prev = nextNode
                        }
                    }
                }

                if (next != null) {
                    node = next
                } else if (toRemove > 0) {
                    throw IllegalStateException("Reached end of list")
                }
                offset = 0
            } while (toRemove > 0)

            return toIdx - fromIdx
        }

        fun removeNode(node: QueueNode) {
            val next = node.next
            val prev = node.prev
            if (prev == null) {
                head = next
            } else {
                prev.next = next
                next?.prev = null
            }
            if (next == null) {
                tail = prev
            } else {
                next.prev = prev
                node.next = null
            }
            size -= node.size()
        }

        fun getPlayableAtIndex(idx: Int): Playable {
            val (offset, node) = getNodeAtIndex(idx)
            return node.getPlayable(offset)
        }

        /**
         * Find the QueueNode where the provided index lies within the node's QueueFragment and return the offset within that
         * QueueFragment (0 if the index points to the start of the QueueFragment / the QueueFragment is a [SinglePlayableQueueFragment])
         */
        fun getNodeAtIndex(idx: Int): Pair<Int, QueueNode> {
            if (idx < 0 || size <= idx) {
                throw IllegalArgumentException("Index $idx out of bounds for size $size")
            }

            if (idx <= size / 2) {
                var currentNode = head!!
                var startIdx = 0
                var nextStartIdx = currentNode.size()

                while (nextStartIdx <= idx) {
                    startIdx = nextStartIdx
                    currentNode = currentNode.next!!
                    nextStartIdx += currentNode.size()
                }

                return Pair(idx - startIdx, currentNode)
            } else {
                var currentNode = tail!!
                var nextStartIdx = size - currentNode.size()

                while (nextStartIdx > idx) {
                    currentNode = currentNode.prev!!
                    nextStartIdx -= currentNode.size()
                }

                return Pair(idx - nextStartIdx, currentNode)
            }
        }

        private fun linkAfter(node: QueueNode, predecessor: QueueNode) {
            val successor = predecessor.next
            node.prev = predecessor
            node.next = successor
            predecessor.next = node
            if (successor == null) {
                tail = node
            } else {
                successor.prev = node
            }
        }

        private fun linkBefore(node: QueueNode, successor: QueueNode) {
            val predecessor = successor.prev
            node.prev = predecessor
            node.next = successor
            successor.prev = node
            if (predecessor == null) {
                head = node
            } else {
                predecessor.next = node
            }
        }
    }

    class QueueNode(val fragment: QueueFragment, val fractureIdx: Int) {
        var next: QueueNode? = null
        var prev: QueueNode? = null

        fun getPlayables(): List<Playable> {
            return fragment.getPlayablesInFracture(fractureIdx)
        }

        fun getPlayable(idx: Int): Playable {
            return fragment.getPlayableInFracture(fractureIdx, idx)
        }

        fun size(): Int {
            return fragment.sizeOfFracture(fractureIdx)
        }

        fun fracture(idx: Int): Int {
            return fragment.addFracture(fractureIdx, idx)
        }
    }

}

package net.robinfriedli.aiode.audio.queue

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.robinfriedli.aiode.Aiode
import net.robinfriedli.aiode.audio.AudioPlayback
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableFactory
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

class AudioQueue(val maxSize: Int?) {

    @Volatile
    var isShuffle: Boolean = false
        set(value) {
            val readLock = lock.readLock()
            readLock.lock()
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
                readLock.unlock()
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
    private val shuffledNodeList: QueueNodeList = QueueNodeList()

    val lock: ReadWriteLock = ReentrantReadWriteLock()

    fun getTracks(): List<Playable> {
        val readLock = lock.readLock()
        readLock.lock()
        try {
            val trackList: MutableList<Playable> = ArrayList(size)
            val nodeList = if (isShuffle) {
                this.shuffledNodeList
            } else {
                this.nodeList
            }

            for (node in nodeList) {
                trackList.addAll(node.getPlayables())
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
            val nodeList = if (isShuffle) {
                this.shuffledNodeList
            } else {
                this.nodeList
            }

            val (offset, queueNode) = nodeList.getNodeAtIndex(idx)
            nodeList.currentNode = queueNode
            queueNode.setPosition(offset)
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
        val nodeList = if (isShuffle) {
            this.shuffledNodeList
        } else {
            this.nodeList
        }

        return if (nodeList.currentNode != null) {
            nodeList.currentNode!!.fragment.current()
        } else {
            throw IllegalStateException("Queue does not have a current track")
        }
    }

    fun iterate(): Playable {
        val writeLock = lock.writeLock()
        writeLock.lock()
        return try {
            iterateLocked()
        } finally {
            writeLock.unlock()
        }
    }

    fun iterateLocked(): Playable {
        val nodeList = if (isShuffle) {
            this.shuffledNodeList
        } else {
            this.nodeList
        }

        return nodeList.iterate()
    }

    fun reverse(): Playable {
        val writeLock = lock.writeLock()
        writeLock.lock()
        return try {
            val nodeList = if (isShuffle) {
                this.shuffledNodeList
            } else {
                this.nodeList
            }

            nodeList.reverse()
        } finally {
            writeLock.unlock()
        }
    }

    fun peekNext(): Playable? {
        val readLock = lock.readLock()
        readLock.lock()
        return try {
            val nodeList = if (isShuffle) {
                this.shuffledNodeList
            } else {
                this.nodeList
            }

            nodeList.peekNext()
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

    fun addContainers(containers: List<PlayableContainer<*>>, playableFactory: PlayableFactory, clear: Boolean) {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            val queueFragments: MutableList<QueueFragment> = ArrayList()
            for (playableContainer in containers) {
                val queueFragment = playableContainer.createQueueFragment(playableFactory, this)
                if (queueFragment != null) {
                    queueFragments.add(queueFragment)
                }
            }

            if (queueFragments.isNotEmpty()) {
                if (clear) {
                    doClear(false)
                }
                for (queueFragment in queueFragments) {
                    doInsert(size, queueFragment)
                }
            } else {
                throw NoResultsFoundException("No results found")
            }
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
                embedBuilder.addField("", "(emtpy)", false)
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

        val nodeList = if (isShuffle) {
            this.shuffledNodeList
        } else {
            this.nodeList
        }

        var node = nodeList.currentNode
        while (node != null && trackList.size < limit) {
            trackList.addAll(node.getNextPlayables(limit - trackList.size))
            node = node.next
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

        val nodeList = if (isShuffle) {
            this.shuffledNodeList
        } else {
            this.nodeList
        }

        var node = nodeList.currentNode
        while (node != null && trackList.size < limit) {
            trackList.addAll(0, node.getPreviousPlayables(limit - trackList.size))
            node = node.prev
        }

        return trackList
    }

    fun reset() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            currIdx = -1
            nodeList.currentNode = null
            shuffledNodeList.currentNode = null

            if (!isEmpty()) {
                iterateLocked()
            }
        } finally {
            writeLock.unlock()
        }
    }

    fun randomize() {
        val writeLock = lock.writeLock()
        writeLock.lock()
        try {
            doRandomize(false)
        } finally {
            writeLock.unlock()
        }
    }

    private fun doRandomize(protectCurrent: Boolean) {
        val handledFragments = HashSet<QueueFragment>()
        val shuffledQueueNodes = LinkedList<QueueNode>()

        val random = Random()
        fun insertRandomFractures(fragment: QueueFragment, protectCurrent: Boolean, initialIdx: Int) {
            val insertIndices: MutableList<Int> = ArrayList()
            insertIndices.add(initialIdx)
            for (i in 0 until fragment.enableShuffle(protectCurrent)) {
                insertIndices.add(random.nextInt(Math.min(1, shuffledQueueNodes.size), shuffledQueueNodes.size + 1))
            }

            var currNodeIdx = insertIndices.size
            insertIndices
                .stream()
                .sorted(Comparator.reverseOrder())
                .forEach { insertIndex -> shuffledQueueNodes.add(insertIndex, QueueNode(fragment, --currNodeIdx)) }
        }

        if (protectCurrent) {
            for (node in nodeList) {
                if (node == nodeList.currentNode) {
                    val fragment = node.fragment
                    handledFragments.add(fragment)
                    insertRandomFractures(fragment, true, 0)
                }
            }
        }

        for (node in nodeList) {
            val fragment = node.fragment

            if (handledFragments.add(fragment)) {
                insertRandomFractures(
                    fragment,
                    false,
                    random.nextInt(Math.min(1, shuffledQueueNodes.size), shuffledQueueNodes.size + 1)
                )
            }
        }

        if (shuffledQueueNodes.isNotEmpty()) {
            var prev: QueueNode? = null
            for (shuffledQueueNode in shuffledQueueNodes) {
                if (prev == null) {
                    shuffledNodeList.head = shuffledQueueNode
                } else {
                    prev.next = shuffledQueueNode
                    shuffledQueueNode.prev = prev
                }

                prev = shuffledQueueNode
            }
            shuffledNodeList.tail = prev
            shuffledNodeList.currentNode = shuffledQueueNodes[0]
            currIdx = 0
        } else {
            shuffledNodeList.clear(false)
            currIdx = -1
        }
    }

    private fun disableShuffle() {
        val handledFragments = HashSet<QueueFragment>()

        val currentFragment = shuffledNodeList.currentNode?.fragment
        // index of the QueueNode corresponding to the fragment's fracture containing the current track
        // e.g. if the current fragment has a fracture at index 3 and 5 and the fragment's current index is 7,
        // the third QueueNode for the fragment (so currentNodeOfFragmentIdx = 2) will contain the current track
        var currentFractureIdx: Int? = null
        if (currentFragment != null) {
            currentFragment.disableShuffle()
            val idxWithinFragment = currentFragment.currentIndex()
            if (idxWithinFragment >= 0) {
                val orderedFractures = currentFragment.getOrderedFractures()
                for (i in 0..orderedFractures.size) {
                    if (i == orderedFractures.size) {
                        currentFractureIdx = i
                    } else if (orderedFractures[i] > idxWithinFragment) {
                        currentFractureIdx = i
                        break
                    }
                }
            } else {
                throw IllegalStateException("Exception disabling shuffle: Current index of current fragment is negative, indicating it wasn't iterated.")
            }
            handledFragments.add(currentFragment)
        }

        if (currentFractureIdx == null) {
            throw IllegalStateException("Could not find index of current fragment fracture")
        }

        var currSize = 0
        for (node in nodeList) {
            val fragment = node.fragment

            if (fragment == currentFragment && currentFractureIdx == node.fractureIdx) {
                nodeList.currentNode = node

                val currentIndex = node.currentIndex()
                if (currentIndex >= 0) {
                    this.currIdx = currSize + currentIndex
                }
            } else if (handledFragments.add(fragment)) {
                fragment.disableShuffle()
            }

            currSize += node.size()
        }
    }

    private fun doInsert(idx: Int, fragment: QueueFragment) {
        if (isShuffle) {
            val random = Random()
            // when appending to the end of the queue use a random index instead, when inserting at a specific index,
            // use that index for the first node
            val targetIdx = if (idx == size) {
                random.nextInt(size + 1)
            } else {
                idx
            }

            val insertIndices: MutableList<Int> = ArrayList()
            insertIndices.add(targetIdx)
            for (i in 0 until fragment.enableShuffle(false)) {
                insertIndices.add(random.nextInt(size + 1))
            }

            // iterating in descending order ensures both that the targetIdx is not invalidated by inserting elements
            // before that index first and that subsequent insertions at the same index will have a lower node index
            var currNodeIdx = insertIndices.size
            insertIndices
                .stream()
                .sorted(Comparator.reverseOrder())
                .forEach { insertIndex -> shuffledNodeList.insert(insertIndex, QueueNode(fragment, --currNodeIdx)) }

            // simply insert added elements to the end of the regular queue while in shuffled mode
            nodeList.insert(size, QueueNode(fragment, 0))
        } else {
            nodeList.insert(idx, QueueNode(fragment, 0))
        }

        if (size == 0) {
            size += fragment.size()
            iterate()
        } else {
            size += fragment.size()
        }
    }

    private fun doClear(retainCurrent: Boolean) {
        val retainedCurrent = nodeList.clear(retainCurrent)
        shuffledNodeList.clear(retainCurrent)

        if (retainedCurrent) {
            size = 1
            currIdx = 0
        } else {
            size = 0
            currIdx = -1
        }
    }

    private fun appendIcon(builder: StringBuilder, unicode: String, enabled: Boolean) {
        if (enabled) {
            builder.append(unicode).append(" ")
        }
    }

    private fun appendPlayable(trackListBuilder: StringBuilder, playable: Playable) {
        playable.fetch()
        var display = playable.display(2, TimeUnit.SECONDS)
        val durationMs = playable.durationMs(2, TimeUnit.SECONDS)
        if (display.length > 100) {
            display = display.substring(0, 95) + "[...]"
        }
        trackListBuilder.append("| ").append(display).append(" - ").append(Util.normalizeMillis(durationMs)).append(System.lineSeparator())
    }

    private inner class QueueNodeList : Iterable<QueueNode> {
        @Volatile
        var head: QueueNode? = null

        @Volatile
        var tail: QueueNode? = null

        @Volatile
        var currentNode: QueueNode? = null

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

        fun iterate(): Playable {
            if (!hasNext()) {
                throw NoResultsFoundException("No next item in queue")
            }

            return if (currentNode != null) {
                if (currentNode!!.hasNextTrack()) {
                    currIdx++
                    return currentNode!!.fragment.next()
                }

                currentNode = if (currentNode!!.next != null) {
                    currIdx++
                    currentNode!!.next
                } else {
                    currIdx = 0
                    head
                }

                if (currentNode!!.isFirstNodeForFragment()) {
                    currentNode!!.fragment.resetPositionToStart()
                }
                currentNode!!.fragment.next()
            } else {
                currentNode = head
                currentNode!!.fragment.resetPositionToStart()
                currIdx++
                currentNode!!.fragment.next()
            }
        }

        fun reverse(): Playable {
            if (!hasPrevious()) {
                throw NoResultsFoundException("No previous item in queue")
            }

            return if (currentNode != null) {
                if (currentNode!!.hasPreviousTrack()) {
                    currIdx--
                    return currentNode!!.fragment.previous()
                }

                currentNode = if (currentNode!!.prev != null) {
                    currIdx--
                    currentNode!!.prev
                } else {
                    currIdx = size - 1
                    tail
                }

                if (currentNode!!.isLastNodeForFragment()) {
                    currentNode!!.fragment.resetPositionToEnd()
                }
                currentNode!!.fragment.previous()
            } else {
                currentNode = tail
                currentNode!!.fragment.resetPositionToEnd()
                currIdx--
                currentNode!!.fragment.previous()
            }
        }

        fun peekNext(): Playable? {
            if (!hasNext()) {
                return null
            }

            return if (currentNode != null) {
                if (currentNode!!.hasNextTrack()) {
                    return currentNode!!.fragment.peekNext()
                }

                val currentNode = currentNode!!.next ?: return null

                if (currentNode.isFirstNodeForFragment()) {
                    currentNode.fragment.resetPositionToStart()
                }
                currentNode.fragment.peekNext()
            } else {
                val currentNode = head!!
                currentNode.fragment.resetPositionToStart()
                currentNode.fragment.peekNext()
            }
        }

        fun clear(retainCurrent: Boolean): Boolean {
            head = null
            tail = null
            currentNode = if (retainCurrent && currentNode != null) {
                QueueNode(currentNode!!.fragment.reduceToCurrentPlayable(), 0)
            } else {
                null
            }

            return currentNode != null
        }

        fun insert(idx: Int, node: QueueNode) {
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
                    node.next = foundNode
                    node.prev = foundNode.prev
                    foundNode.prev?.next = node
                    foundNode.prev = node
                } else {
                    // new node is inserted within the found node, fracture the found node and place new node between the two fractures
                    val fractureIdx = foundNode.fragment.addFracture(offset)
                    val fracturedNode = QueueNode(foundNode.fragment, fractureIdx)

                    node.prev = foundNode
                    node.next = fracturedNode

                    fracturedNode.prev = node
                    fracturedNode.next = foundNode.next
                    foundNode.next?.prev = fracturedNode

                    foundNode.next = node
                }
            }
        }

        /**
         * Find the QueueNode where the provided index lies within the node's QueueFragment and return the offset within that
         * QueueFragment (0 if the index points to the start of the QueueFragment / the QueueFragment is a [SinglePlayableQueueFragment])
         */
        fun getNodeAtIndex(idx: Int): Pair<Int, QueueNode> {
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

    }

    class QueueNode(val fragment: QueueFragment, val fractureIdx: Int) {
        var next: QueueNode? = null
        var prev: QueueNode? = null

        fun getPlayables(): List<Playable> {
            return fragment.getPlayablesInFracture(fractureIdx)
        }

        fun getNextPlayables(limit: Int): List<Playable> {
            return fragment.getNextPlayablesInFracture(fractureIdx, limit)
        }

        fun getPreviousPlayables(limit: Int): List<Playable> {
            return fragment.getPreviousPlayablesInFracture(fractureIdx, limit)
        }

        fun size(): Int {
            return fragment.sizeOfFracture(fractureIdx)
        }

        fun currentIndex(): Int {
            return fragment.currentIndexWithinFracture(fractureIdx)
        }

        fun setPosition(idx: Int) {
            fragment.setPosition(fractureIdx, idx)
        }

        fun hasNextTrack(): Boolean {
            return fragment.hasNext(fractureIdx)
        }

        fun hasPreviousTrack(): Boolean {
            return fragment.hasPrevious(fractureIdx)
        }

        fun isFirstNodeForFragment(): Boolean {
            return fractureIdx == 0
        }

        fun isLastNodeForFragment(): Boolean {
            return fragment.getOrderedFractures().size == 1
        }
    }

}

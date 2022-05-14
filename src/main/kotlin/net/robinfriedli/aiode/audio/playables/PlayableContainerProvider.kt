package net.robinfriedli.aiode.audio.playables

import net.robinfriedli.aiode.util.ClassDescriptorNode

/**
 * Interface for singleton components that provide a [PlayableContainer] instance for an object of a specific type.
 */
interface PlayableContainerProvider<T> : ClassDescriptorNode {

    override fun getType(): Class<T>

    fun getPlayableContainer(item: T): PlayableContainer<T>

}

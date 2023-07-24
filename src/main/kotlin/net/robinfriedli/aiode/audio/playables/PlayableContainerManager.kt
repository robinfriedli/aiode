package net.robinfriedli.aiode.audio.playables

import net.robinfriedli.aiode.util.ClassDescriptorNode
import org.springframework.stereotype.Component

@Component
open class PlayableContainerManager(private val playableContainerProviders: List<PlayableContainerProvider<*>>) {

    /**
     * Finds a registered [PlayableContainerProvider] for the type of the provided item. This also finds [PlayableContainerProvider]
     * for supertypes, casting them down to the type of of the provided item, which is safe since the type parameter exclusively
     * refers to the type of the item.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getPlayableContainer(item: T): PlayableContainer<T>? {
        return playableContainerProviders.stream()
            .filter { playableContainerProvider -> playableContainerProvider.type.isAssignableFrom(item.javaClass) }
            .sorted(ClassDescriptorNode.getComparator())
            .findFirst()
            .map { playableContainerProvider -> (playableContainerProvider as PlayableContainerProvider<T>).getPlayableContainer(item) }
            .orElse(null)
    }

    fun <T : Any> requirePlayableContainer(item: T): PlayableContainer<T> {
        return getPlayableContainer(item)
            ?: throw java.util.NoSuchElementException("No playable container provider found for item of type ${item::class.java}")
    }

}

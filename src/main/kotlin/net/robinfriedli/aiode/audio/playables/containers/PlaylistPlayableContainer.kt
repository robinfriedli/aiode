package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.*
import net.robinfriedli.aiode.entities.Playlist
import net.robinfriedli.aiode.function.SpotifyInvoker
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

class PlaylistPlayableContainer(
    playlist: Playlist,
    private val playableContainerManager: PlayableContainerManager
) : AbstractPlayableContainer<Playlist>(playlist) {

    private val spotifyInvoker: SpotifyInvoker = SpotifyInvoker.createForCurrentContext()

    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        val items = spotifyInvoker.invokeFunction { spotifyApi -> getItem().getTracks(spotifyApi) }

        val playableContainers = items
            .stream()
            .map { track -> playableContainerManager.requirePlayableContainer(track) }
            .toList()

        return playableFactory.loadAll(playableContainers)
    }
}

@Component
class PlaylistPlayableContainerProvider(
    @Lazy private val playableContainerManager: PlayableContainerManager
) : PlayableContainerProvider<Playlist> {

    override fun getType(): Class<Playlist> {
        return Playlist::class.java
    }

    override fun getPlayableContainer(item: Playlist): PlayableContainer<Playlist> {
        return PlaylistPlayableContainer(item, playableContainerManager)
    }

}

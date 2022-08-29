package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractPlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component
import se.michaelthelin.spotify.model_objects.specification.Album
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified

class SpotifyAlbumPlayableContainer(spotifyAlbum: Album) : AbstractPlayableContainer<Album>(spotifyAlbum) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyAlbumPlayableContainerProvider : PlayableContainerProvider<Album> {
    override fun getType(): Class<Album> {
        return Album::class.java
    }

    override fun getPlayableContainer(item: Album): PlayableContainer<Album> {
        return SpotifyAlbumPlayableContainer(item)
    }
}

class SpotifyAlbumSimplifiedPlayableContainer(spotifyAlbum: AlbumSimplified) : AbstractPlayableContainer<AlbumSimplified>(spotifyAlbum) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyAlbumSimplifiedPlayableContainerProvider : PlayableContainerProvider<AlbumSimplified> {
    override fun getType(): Class<AlbumSimplified> {
        return AlbumSimplified::class.java
    }

    override fun getPlayableContainer(item: AlbumSimplified): PlayableContainer<AlbumSimplified> {
        return SpotifyAlbumSimplifiedPlayableContainer(item)
    }
}

package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractPlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component
import se.michaelthelin.spotify.model_objects.specification.Playlist
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified

class SpotifyPlaylistPlayableContainer(spotifyPlaylist: Playlist) : AbstractPlayableContainer<Playlist>(spotifyPlaylist) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyPlaylistPlayableContainerProvider : PlayableContainerProvider<Playlist> {
    override fun getType(): Class<Playlist> {
        return Playlist::class.java
    }

    override fun getPlayableContainer(item: Playlist): PlayableContainer<Playlist> {
        return SpotifyPlaylistPlayableContainer(item)
    }
}

class SpotifyPlaylistSimplifiedPlayableContainer(spotifyPlaylist: PlaylistSimplified) : AbstractPlayableContainer<PlaylistSimplified>(spotifyPlaylist) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyPlaylistSimplifiedPlayableContainerProvider : PlayableContainerProvider<PlaylistSimplified> {
    override fun getType(): Class<PlaylistSimplified> {
        return PlaylistSimplified::class.java
    }

    override fun getPlayableContainer(item: PlaylistSimplified): PlayableContainer<PlaylistSimplified> {
        return SpotifyPlaylistSimplifiedPlayableContainer(item)
    }
}

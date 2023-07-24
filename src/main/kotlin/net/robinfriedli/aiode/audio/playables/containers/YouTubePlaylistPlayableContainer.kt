package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractPlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist
import org.springframework.stereotype.Component

class YouTubePlaylistPlayableContainer(youTubePlaylist: YouTubePlaylist) : AbstractPlayableContainer<YouTubePlaylist>(youTubePlaylist) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class YouTubePlaylistPlayableContainerProvider : PlayableContainerProvider<YouTubePlaylist> {
    override fun getType(): Class<YouTubePlaylist> {
        return YouTubePlaylist::class.java
    }

    override fun getPlayableContainer(item: YouTubePlaylist): PlayableContainer<YouTubePlaylist> {
        return YouTubePlaylistPlayableContainer(item)
    }
}

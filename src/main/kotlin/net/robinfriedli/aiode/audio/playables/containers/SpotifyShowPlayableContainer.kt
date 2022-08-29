package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractPlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import org.springframework.stereotype.Component
import se.michaelthelin.spotify.model_objects.specification.Show
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified

class SpotifyShowPlayableContainer(spotifyShow: Show) : AbstractPlayableContainer<Show>(spotifyShow) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyShowPlayableContainerProvider : PlayableContainerProvider<Show> {

    override fun getType(): Class<Show> {
        return Show::class.java
    }

    override fun getPlayableContainer(item: Show): PlayableContainer<Show> {
        return SpotifyShowPlayableContainer(item)
    }
}

class SpotifyShowSimplifiedPlayableContainer(spotifyShow: ShowSimplified) : AbstractPlayableContainer<ShowSimplified>(spotifyShow) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class SpotifyShowSimplifiedPlayableContainerProvider : PlayableContainerProvider<ShowSimplified> {

    override fun getType(): Class<ShowSimplified> {
        return ShowSimplified::class.java
    }

    override fun getPlayableContainer(item: ShowSimplified): PlayableContainer<ShowSimplified> {
        return SpotifyShowSimplifiedPlayableContainer(item)
    }

}

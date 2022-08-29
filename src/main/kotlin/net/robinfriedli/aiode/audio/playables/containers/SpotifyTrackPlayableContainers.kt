package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.AbstractSinglePlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainer
import net.robinfriedli.aiode.audio.playables.PlayableContainerProvider
import net.robinfriedli.aiode.audio.playables.PlayableFactory
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack
import org.springframework.stereotype.Component
import se.michaelthelin.spotify.model_objects.specification.Episode
import se.michaelthelin.spotify.model_objects.specification.Track

class SpotifyTrackPlayableContainer(spotifyTrack: SpotifyTrack) : AbstractSinglePlayableContainer<SpotifyTrack>(spotifyTrack) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return playableFactory.createPlayable(getItem())
    }
}

@Component
class SpotifyTrackPlayableContainerProvider : PlayableContainerProvider<SpotifyTrack> {
    override fun getType(): Class<SpotifyTrack> {
        return SpotifyTrack::class.java
    }

    override fun getPlayableContainer(item: SpotifyTrack): PlayableContainer<SpotifyTrack> {
        return SpotifyTrackPlayableContainer(item)
    }
}

class TrackPlayableContainer(track: Track) : AbstractSinglePlayableContainer<Track>(track) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return playableFactory.createPlayable(SpotifyTrack.wrap(getItem()))
    }
}

@Component
class TrackPlayableContainerProvider : PlayableContainerProvider<Track> {
    override fun getType(): Class<Track> {
        return Track::class.java
    }

    override fun getPlayableContainer(item: Track): PlayableContainer<Track> {
        return TrackPlayableContainer(item)
    }
}

class EpisodePlayableContainer(episode: Episode) : AbstractSinglePlayableContainer<Episode>(episode) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return playableFactory.createPlayable(SpotifyTrack.wrap(getItem()))
    }
}

@Component
class EpisodePlayableContainerProvider : PlayableContainerProvider<Episode> {
    override fun getType(): Class<Episode> {
        return Episode::class.java
    }

    override fun getPlayableContainer(item: Episode): PlayableContainer<Episode> {
        return EpisodePlayableContainer(item)
    }
}

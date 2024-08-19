package net.robinfriedli.aiode.filebroker

import com.google.common.base.Strings
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.entities.User
import net.robinfriedli.aiode.Aiode
import net.robinfriedli.aiode.audio.AbstractSoftCachedPlayable
import net.robinfriedli.aiode.audio.AudioTrackLoader
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.entities.FilebrokerTrack
import net.robinfriedli.aiode.entities.Playlist
import net.robinfriedli.aiode.entities.PlaylistItem
import net.robinfriedli.filebroker.FilebrokerApi
import org.hibernate.Session
import org.postgresql.util.PGInterval
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit

class FilebrokerPlayableWrapper(val post: FilebrokerApi.Post) : AbstractSoftCachedPlayable() {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    override fun getPlaybackUrl(): String {
        val uri =
            URI.create(Aiode.get().filebrokerApi.baseUrl).resolve("get-object/").resolve(post.s3_object.object_key)
        return uri.toString()
    }

    override fun getId(): String {
        return post.pk.toString()
    }

    override fun getTitle(): String {
        return post.title ?: post.s3_object_metadata.title ?: ("Post " + post.pk)
    }

    override fun getTitle(timeOut: Long, unit: TimeUnit?): String {
        return getTitle()
    }

    override fun getTitleNow(alternativeValue: String?): String {
        return getTitle()
    }

    override fun getDisplay(): String {
        val title = getTitle()
        val artist = post.s3_object_metadata.artist ?: post.s3_object_metadata.album_artist
        return if (Strings.isNullOrEmpty(artist)) {
            title
        } else {
            String.format("%s by %s", title, artist)
        }
    }

    override fun getDisplay(timeOut: Long, unit: TimeUnit?): String {
        return getDisplay()
    }

    override fun getDisplayNow(alternativeValue: String?): String {
        return getDisplay()
    }

    override fun getDurationMs(): Long {
        return getDurationMs(5, TimeUnit.SECONDS)
    }

    override fun getDurationMs(timeOut: Long, unit: TimeUnit?): Long {
        val cached = cached
        if (cached != null) {
            return cached.duration
        } else if (post.s3_object_metadata.duration != null) {
            try {
                val pgInterval = PGInterval(post.s3_object_metadata.duration)
                val cal = GregorianCalendar()
                cal.timeInMillis = 0
                pgInterval.add(cal)
                return cal.timeInMillis
            } catch (e: Exception) {
                logger.error(
                    "Failed to parse filebroker post ${post.pk} metadata duration ${post.s3_object_metadata.duration}",
                    e
                )
            }
        }

        try {
            val audioTrackLoader = AudioTrackLoader(Aiode.get().audioManager.playerManager)
            val res = audioTrackLoader.loadByIdentifier(playbackUrl, timeOut, unit)
            if (res is AudioTrack) {
                setCached(res)
                return res.duration
            }
        } catch (e: Exception) {
            logger.error("Failed to load audio track for filebroker post ${post.pk} to load audio duration", e)
        }
        return 0
    }

    override fun getDurationNow(alternativeValue: Long): Long {
        val cached = cached
        if (cached != null) {
            return cached.duration
        } else if (post.s3_object_metadata.duration != null) {
            try {
                val pgInterval = PGInterval(post.s3_object_metadata.duration)
                val cal = GregorianCalendar()
                cal.timeInMillis = 0
                pgInterval.add(cal)
                return cal.timeInMillis
            } catch (e: Exception) {
                logger.error(
                    "Failed to parse filebroker post ${post.pk} metadata duration ${post.s3_object_metadata.duration}",
                    e
                )
            }
        }

        return alternativeValue
    }

    override fun getAlbumCoverUrl(): String? {
        if (post.thumbnail_url != null) {
            return post.thumbnail_url
        } else if (post.s3_object.thumbnail_object_key != null) {
            val uri = URI.create(Aiode.get().filebrokerApi.baseUrl).resolve("get-object/")
                .resolve(post.s3_object.thumbnail_object_key)
            return uri.toString()
        } else {
            return null
        }
    }

    override fun export(playlist: Playlist?, user: User?, session: Session?): PlaylistItem {
        return FilebrokerTrack(post, user, playlist)
    }

    override fun getSource(): Playable.Source {
        return Playable.Source.FILEBROKER
    }

}

package net.robinfriedli.aiode.audio.playables

import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.robinfriedli.aiode.Aiode
import net.robinfriedli.aiode.audio.AudioTrackLoader
import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.exec.SpotifyTrackRedirectionRunnable
import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor
import net.robinfriedli.aiode.audio.exec.TrackLoadingRunnable
import net.robinfriedli.aiode.audio.exec.YouTubePlaylistPopulationRunnable
import net.robinfriedli.aiode.audio.playables.containers.*
import net.robinfriedli.aiode.audio.spotify.PlayableTrackWrapper
import net.robinfriedli.aiode.audio.spotify.SpotifyService
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackRedirect
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo
import net.robinfriedli.aiode.audio.youtube.YouTubePlaylist
import net.robinfriedli.aiode.audio.youtube.YouTubeService
import net.robinfriedli.aiode.exceptions.CommandRuntimeException
import net.robinfriedli.aiode.exceptions.InvalidCommandException
import net.robinfriedli.aiode.exceptions.NoResultsFoundException
import net.robinfriedli.aiode.function.ChainableRunnable
import net.robinfriedli.aiode.function.CheckedFunction
import net.robinfriedli.aiode.function.SpotifyInvoker
import net.robinfriedli.filebroker.FilebrokerApi
import net.robinfriedli.stringlist.StringList
import org.apache.hc.core5.http.ParseException
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException
import se.michaelthelin.spotify.model_objects.specification.*
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.stream.Collectors

class PlayableFactory(
    val audioTrackLoader: AudioTrackLoader,
    val spotifyService: SpotifyService,
    val trackLoadingExecutor: TrackLoadingExecutor,
    val youTubeService: YouTubeService,
    val redirectSpotify: Boolean,
    val filebrokerApi: FilebrokerApi,
) {

    companion object {
        @JvmStatic
        val BASE62_REGEX = "^[a-zA-Z0-9]{22}\$".toRegex()
        @JvmStatic
        val FILEBROKER_POST_REGEX = ".*/post/([0-9]+)(\\?.*)?".toRegex()
        @JvmStatic
        val FILEBROKER_COLLECTION_REGEX = ".*/collection/([0-9]+)(\\?.*)?".toRegex()
        @JvmStatic
        val FILEBROKER_COLLECTION_ITEM_REGEX = ".*/collection/([0-9]+)/post/([0-9]+)(\\?.*)?".toRegex()
    }

    private val spotifyInvoker: SpotifyInvoker = SpotifyInvoker.createForCurrentContext()

    private var batchLoadingTasks: MutableMap<Class<*>, TrackLoadingRunnable<*>>? = null

    fun loadAll(playableContainers: List<PlayableContainer<*>>): List<Playable> {
        val playables = Lists.newArrayList<Playable>()
        val tasks = setLoadingBatch()
        try {
            for (playableContainer in playableContainers) {
                playables.addAll(playableContainer.loadPlayables(this))
            }

            var combinedTask: ChainableRunnable? = null
            for (task in tasks.values) {
                combinedTask = if (combinedTask == null) {
                    task
                } else {
                    combinedTask.andThen(task)
                }
            }

            if (combinedTask != null) {
                trackLoadingExecutor.execute(combinedTask)
            }
        } finally {
            unsetLoadingBatch()
        }

        return playables
    }

    fun createPlayable(spotifyTrack: SpotifyTrack): Playable {
        return if (redirectSpotify) {
            val spotifyTrackRedirect = SpotifyTrackRedirect(spotifyTrack, youTubeService)
            executeOrAppendTrackLoadingTask(SpotifyTrackRedirectionRunnable::class.java, spotifyTrackRedirect) { trackRedirect ->
                SpotifyTrackRedirectionRunnable(filebrokerApi, youTubeService, trackRedirect)
            }
            spotifyTrackRedirect
        } else {
            PlayableTrackWrapper(spotifyTrack)
        }
    }

    fun createPlayables(youTubePlaylist: YouTubePlaylist): List<Playable> {
        executeOrAppendTrackLoadingTask(YouTubePlaylistPopulationRunnable::class.java, youTubePlaylist) { playlist ->
            YouTubePlaylistPopulationRunnable(youTubeService, playlist)
        }

        return Lists.newArrayList(youTubePlaylist.videos)
    }

    fun createPlayables(spotifyPlaylist: PlaylistSimplified): List<Playable> {
        val tracks = spotifyInvoker.invoke<List<SpotifyTrack>> { spotifyService.getPlaylistTracks(spotifyPlaylist) }
        val trackPlayableContainers = tracks.stream().map { track -> SpotifyTrackPlayableContainer(track) }.collect(Collectors.toList())

        return loadAll(trackPlayableContainers)
    }

    fun createPlayables(spotifyPlaylist: Playlist): List<Playable> {
        val tracks = spotifyInvoker.invoke<List<SpotifyTrack>> { spotifyService.getPlaylistTracks(spotifyPlaylist.id) }
        val trackPlayableContainers = tracks.stream().map { track -> SpotifyTrackPlayableContainer(track) }.collect(Collectors.toList())

        return loadAll(trackPlayableContainers)
    }

    fun createPlayables(spotifyAlbum: AlbumSimplified): List<Playable> {
        val tracks = spotifyInvoker.invoke(Callable { spotifyService.getAlbumTracks(spotifyAlbum) })
        val trackPlayableContainers = tracks.stream().map { track -> SpotifyTrackPlayableContainer(SpotifyTrack.wrap(track!!)) }.collect(Collectors.toList())

        return loadAll(trackPlayableContainers)
    }

    fun createPlayables(spotifyAlbum: Album): List<Playable> {
        val tracks = spotifyInvoker.invoke(Callable { spotifyService.getAlbumTracks(spotifyAlbum.id) })
        val trackPlayableContainers = tracks.stream().map { track -> SpotifyTrackPlayableContainer(SpotifyTrack.wrap(track!!)) }.collect(Collectors.toList())

        return loadAll(trackPlayableContainers)
    }

    fun createPlayables(spotifyShow: Show): List<Playable> {
        return createPlayablesForSpotifyShow(spotifyShow.id)
    }

    fun createPlayables(spotifyShow: ShowSimplified): List<Playable> {
        return createPlayablesForSpotifyShow(spotifyShow.id)
    }

    fun createPlayablesForSpotifyShow(showId: String): List<Playable> {
        val tracks = spotifyInvoker.invoke(Callable { spotifyService.getShowEpisodes(showId) })
        val trackPlayableContainers = tracks.stream().map { episode -> SpotifyTrackPlayableContainer(SpotifyTrack.wrap(episode!!)) }.collect(Collectors.toList())

        return loadAll(trackPlayableContainers)
    }

    fun createPlayables(filebrokerCollection: FilebrokerApi.PostCollection): List<Playable> {
        val items = ArrayList<FilebrokerApi.PostCollectionItem>()
        var page = 0L
        do {
            val result = filebrokerApi.searchPostCollectionItemsAsync(filebrokerCollection.pk, page = page, limit = 100).get(10, TimeUnit.SECONDS)
            val currItems = result.collection_items ?: emptyList()
            items.addAll(currItems)

            if (currItems.size < 100) {
                break
            }
            page++
        } while (true)

        val postContainers = items.map { item -> FilebrokerPostPlayableContainer(item.post) }
        return loadAll(postContainers)
    }

    @Throws(IOException::class)
    fun createPlayableContainerForUrl(url: String): PlayableContainer<*> {
        val uri = try {
            URI.create(url)
        } catch (e: IllegalArgumentException) {
            throw InvalidCommandException("'$url' is not a valid URL")
        }

        val filebrokerBaseUrl = Aiode.get().springPropertiesConfig.getApplicationProperty("aiode.filebroker.api_base_url")
        val filebrokerBaseUri = filebrokerBaseUrl?.let { URI.create(it) }
        return when {
            uri.host.contains("youtube.com") -> {
                val parameterMap = getParameterMap(uri)
                val videoId = parameterMap["v"]
                val playlistId = parameterMap["list"]

                when {
                    videoId != null -> {
                        val youTubeVideo = youTubeService.requireVideoForId(videoId)
                        SinglePlayableContainer(youTubeVideo)
                    }
                    playlistId != null -> {
                        val youTubePlaylist = youTubeService.playlistForId(playlistId)
                        YouTubePlaylistPlayableContainer(youTubePlaylist)
                    }
                    else -> throw InvalidCommandException("Detected YouTube URL but no video or playlist id provided.")
                }
            }
            uri.host == "youtu.be" -> {
                val parts = uri.path.split("/").toTypedArray()
                val youTubeVideo = youTubeService.requireVideoForId(parts[parts.size - 1])
                SinglePlayableContainer(youTubeVideo)
            }
            uri.host == "open.spotify.com" -> {
                try {
                    createPlayableContainerFromSpotifyUrl(uri, spotifyService.spotifyApi)
                } catch (e: NotFoundException) {
                    throw NoResultsFoundException("No results found for provided spotify URL", e)
                } catch (e: CommandRuntimeException) {
                    if (e.cause is NotFoundException) {
                        throw NoResultsFoundException("No results found for provided spotify URL", e.cause)
                    } else {
                        throw e
                    }
                }
            }
            (uri.host == "filebroker.io" || (filebrokerBaseUri != null && uri.host == filebrokerBaseUri.host)) && !uri.path.startsWith("/api/get-object/") -> {
                val postPkStr = FILEBROKER_POST_REGEX.find(url)?.groups?.get(1)?.value
                val postCollectionPk = FILEBROKER_COLLECTION_REGEX.find(url)?.groups?.get(1)?.value
                val postCollectionItemMatch = FILEBROKER_COLLECTION_ITEM_REGEX.find(url)

                return try {
                    if (postCollectionItemMatch != null && postCollectionItemMatch.groups.size >= 3) {
                        val postCollectionPk = postCollectionItemMatch.groups[1]!!.value
                        val postCollectionItemPk = postCollectionItemMatch.groups[2]!!.value
                        val post = filebrokerApi.getPostCollectionItemAsync(postCollectionPk.toLong(), postCollectionItemPk.toLong()).get(10, TimeUnit.SECONDS)
                        FilebrokerPostDetailedPlayableContainer(post)
                    } else if (postPkStr != null) {
                        val post = filebrokerApi.getPostAsync(postPkStr.toLong()).get(10, TimeUnit.SECONDS)
                        FilebrokerPostDetailedPlayableContainer(post)
                    } else if (postCollectionPk != null) {
                        val postCollection = filebrokerApi.getPostCollectionAsync(postCollectionPk.toLong()).get(10, TimeUnit.SECONDS)
                        FilebrokerPostCollectionDetailedPlayableContainer(postCollection)
                    } else {
                        throw InvalidCommandException("Filebroker URL unsupported, must link to a post or collection.")
                    }
                } catch (e: NumberFormatException) {
                    throw InvalidCommandException("Filebroker URL is invalid")
                } catch (e: ExecutionException) {
                    if (e.cause is FilebrokerApi.InvalidHttpResponseException && (e.cause as FilebrokerApi.InvalidHttpResponseException).status == 403) {
                        throw NoResultsFoundException("Aiode could not access the provided filebroker URL. Make sure the content is public or shared with the aiode community or supporters group.")
                    } else {
                        throw e
                    }
                }
            }
            else -> createPlayableContainerFromUrl(uri.toString())
        }
    }

    fun setLoadingBatch(): MutableMap<Class<*>, TrackLoadingRunnable<*>> {
        if (batchLoadingTasks == null) {
            batchLoadingTasks = HashMap()
        }
        return batchLoadingTasks!!
    }

    fun unsetLoadingBatch() {
        batchLoadingTasks = null
    }

    private fun createPlayableContainerFromUrl(url: String): PlayableContainer<*> {
        val audioItem = audioTrackLoader.loadByIdentifier(url)
            ?: throw NoResultsFoundException("Could not load audio for provided URL.")

        return when (audioItem) {
            is AudioTrack -> AudioTrackPlayableContainer(audioItem)
            is AudioPlaylist -> AudioPlaylistPlayableContainer(audioItem)
            else -> throw UnsupportedOperationException("Expected an AudioTrack or AudioPlaylist but got " + audioItem.javaClass.simpleName)
        }
    }

    private fun createPlayableContainerFromSpotifyUrl(uri: URI, spotifyApi: SpotifyApi): PlayableContainer<*> {
        val pathFragments = StringList.createWithRegex(uri.path, "/")

        return when {
            pathFragments.contains("playlist") -> {
                createPlayableForSpotifyUrlType(pathFragments, "playlist") { playlistId ->
                    val playlist = spotifyService.getPlaylist(playlistId)
                    SpotifyPlaylistPlayableContainer(playlist)
                }
            }
            pathFragments.contains("track") -> {
                createPlayableForSpotifyUrlType(pathFragments, "track") { trackId ->
                    val track = spotifyApi.getTrack(trackId).build().execute()
                    TrackPlayableContainer(track)
                }
            }
            pathFragments.contains("episode") -> {
                createPlayableForSpotifyUrlType(pathFragments, "episode") { episodeId ->
                    val episode = spotifyApi.getEpisode(episodeId).build().execute()
                    EpisodePlayableContainer(episode)
                }
            }
            pathFragments.contains("album") -> {
                createPlayableForSpotifyUrlType(pathFragments, "album") { albumId ->
                    val album = spotifyService.getAlbum(albumId)
                    SpotifyAlbumPlayableContainer(album)
                }
            }
            pathFragments.contains("show") -> {
                createPlayableForSpotifyUrlType(pathFragments, "show") { showId ->
                    val show = spotifyService.getShow(showId)
                    SpotifyShowPlayableContainer(show)
                }
            }
            else -> throw InvalidCommandException("Detected Spotify URL but no track, playlist, album, episode or show id provided.")
        }
    }

    private fun createPlayableForSpotifyUrlType(pathFragments: StringList, type: String, loadFunc: CheckedFunction<String, PlayableContainer<*>>): PlayableContainer<*> {
        val id = pathFragments.tryGet(pathFragments.indexOf(type) + 1)
        if (Strings.isNullOrEmpty(id)) {
            throw InvalidCommandException("No $type ID provided")
        }
        if (!BASE62_REGEX.matches(id!!)) {
            throw InvalidCommandException("'$id' is not a valid Spotify ID")
        }
        return try {
            spotifyInvoker.invoke(Callable { loadFunc.apply(id) })
        } catch (e: NotFoundException) {
            throw NoResultsFoundException("No Spotify $type found for id '$id'")
        } catch (e: IOException) {
            throw RuntimeException("Exception during Spotify request", e)
        } catch (e: SpotifyWebApiException) {
            throw RuntimeException("Exception during Spotify request", e)
        } catch (e: ParseException) {
            throw RuntimeException("Exception during Spotify request", e)
        }
    }

    private fun getParameterMap(uri: URI): Map<String, String> {
        val parameters = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8)
        return parameters.stream().collect(Collectors.toMap({ obj: NameValuePair -> obj.name }, { obj: NameValuePair -> obj.value }))
    }

    /**
     * Submit the provided [TrackLoadingRunnable] for execution or append it to the list of [TrackLoadingRunnable] instances
     * of the current batch ([batchLoadingTasks]) if not null.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <I : Any, T : TrackLoadingRunnable<I>> executeOrAppendTrackLoadingTask(
        trackLoadingRunnableType: Class<T>,
        item: I,
        initialisationFunction: Function<I, T>
    ) {
        executeOrAppendTrackLoadingTask(
            trackLoadingRunnableType,
            Collections.singletonList(item)
        ) { t ->
            initialisationFunction.apply(t[0])
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I : Any, T : TrackLoadingRunnable<I>> executeOrAppendTrackLoadingTask(trackLoadingRunnableType: Class<T>, items: List<I>, initialisationFunction: Function<List<I>, T>) {
        if (batchLoadingTasks != null) {
            val existingTask: TrackLoadingRunnable<I>? = batchLoadingTasks!![trackLoadingRunnableType] as TrackLoadingRunnable<I>?

            if (existingTask != null) {
                existingTask.addItems(items)
            } else {
                batchLoadingTasks!![trackLoadingRunnableType] = initialisationFunction.apply(items)
            }
        } else {
            trackLoadingExecutor.execute(initialisationFunction.apply(items))
        }
    }

}

package net.robinfriedli.aiode.audio.playables.containers

import net.robinfriedli.aiode.audio.Playable
import net.robinfriedli.aiode.audio.playables.*
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper
import net.robinfriedli.filebroker.FilebrokerApi
import org.springframework.stereotype.Component

class FilebrokerPostPlayableContainer(post: FilebrokerApi.Post) :
    AbstractSinglePlayableContainer<FilebrokerApi.Post>(post) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return FilebrokerPlayableWrapper(getItem())
    }
}

@Component
class FilebrokerPostPlayableContainerProvider : PlayableContainerProvider<FilebrokerApi.Post> {
    override fun getType(): Class<FilebrokerApi.Post> {
        return FilebrokerApi.Post::class.java
    }

    override fun getPlayableContainer(item: FilebrokerApi.Post): PlayableContainer<FilebrokerApi.Post> {
        return FilebrokerPostPlayableContainer(item)
    }
}

class FilebrokerPostDetailedPlayableContainer(post: FilebrokerApi.PostDetailed) :
    AbstractSinglePlayableContainer<FilebrokerApi.PostDetailed>(post) {
    override fun doLoadPlayable(playableFactory: PlayableFactory): Playable {
        return FilebrokerPlayableWrapper(FilebrokerApi.Post(getItem()))
    }
}

@Component
class FilebrokerPostDetailedPlayableContainerProvider : PlayableContainerProvider<FilebrokerApi.PostDetailed> {
    override fun getType(): Class<FilebrokerApi.PostDetailed> {
        return FilebrokerApi.PostDetailed::class.java
    }

    override fun getPlayableContainer(item: FilebrokerApi.PostDetailed): PlayableContainer<FilebrokerApi.PostDetailed> {
        return FilebrokerPostDetailedPlayableContainer(item)
    }
}

class FilebrokerSearchResultPlayableContainer(searchResult: FilebrokerApi.SearchResult) :
    AbstractPlayableContainer<FilebrokerApi.SearchResult>(searchResult) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        val searchResult = getItem()
        if (!searchResult.posts.isNullOrEmpty()) {
            return searchResult.posts.map { post -> FilebrokerPlayableWrapper(post) }
        }
        if (!searchResult.collection_items.isNullOrEmpty()) {
            return searchResult.collection_items.map { item -> FilebrokerPlayableWrapper(item.post) }
        }
        return emptyList()
    }

}

@Component
class FilebrokerSearchResultPlayableContainerProvider : PlayableContainerProvider<FilebrokerApi.SearchResult> {
    override fun getType(): Class<FilebrokerApi.SearchResult> {
        return FilebrokerApi.SearchResult::class.java
    }

    override fun getPlayableContainer(item: FilebrokerApi.SearchResult): PlayableContainer<FilebrokerApi.SearchResult> {
        return FilebrokerSearchResultPlayableContainer(item)
    }
}

class FilebrokerPostCollectionPlayableContainer(collection: FilebrokerApi.PostCollection) :
    AbstractPlayableContainer<FilebrokerApi.PostCollection>(collection) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(getItem())
    }
}

@Component
class FilebrokerPostCollectionPlayableContainerProvider :
    PlayableContainerProvider<FilebrokerApi.PostCollection> {
    override fun getType(): Class<FilebrokerApi.PostCollection> {
        return FilebrokerApi.PostCollection::class.java
    }

    override fun getPlayableContainer(item: FilebrokerApi.PostCollection): PlayableContainer<FilebrokerApi.PostCollection> {
        return FilebrokerPostCollectionPlayableContainer(item)
    }
}

class FilebrokerPostCollectionDetailedPlayableContainer(collection: FilebrokerApi.PostCollectionDetailed) :
    AbstractPlayableContainer<FilebrokerApi.PostCollectionDetailed>(collection) {
    override fun doLoadPlayables(playableFactory: PlayableFactory): List<Playable> {
        return playableFactory.createPlayables(FilebrokerApi.PostCollection(getItem()))
    }
}

@Component
class FilebrokerPostCollectionDetailedPlayableContainerProvider :
    PlayableContainerProvider<FilebrokerApi.PostCollectionDetailed> {
    override fun getType(): Class<FilebrokerApi.PostCollectionDetailed> {
        return FilebrokerApi.PostCollectionDetailed::class.java
    }

    override fun getPlayableContainer(item: FilebrokerApi.PostCollectionDetailed): PlayableContainer<FilebrokerApi.PostCollectionDetailed> {
        return FilebrokerPostCollectionDetailedPlayableContainer(item)
    }
}

package net.robinfriedli.filebroker

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.robinfriedli.aiode.exceptions.RateLimitException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class FilebrokerApi(
    val baseUrl: String = BASE_URL,
    currentLogin: Login? = null,
    var loginChangeCallback: ((Login?) -> Unit)? = null
) {
    companion object {
        @JvmStatic
        val BASE_URL: String = "https://filebroker.io/api/"

        @JvmStatic
        val RATE_LIMITER_REGISTRY: RateLimiterRegistry = RateLimiterRegistry.ofDefaults()
    }

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val rateLimiter: RateLimiter = RATE_LIMITER_REGISTRY.rateLimiter(
        "filebroker",
        RateLimiterConfig
            .custom()
            .limitForPeriod(50)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(15))
            .build()
    )

    private val searchRateLimiter: RateLimiter = RATE_LIMITER_REGISTRY.rateLimiter(
        "filebroker-search",
        RateLimiterConfig
            .custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(15))
            .build()
    )

    private val authRateLimiter: RateLimiter = RATE_LIMITER_REGISTRY.rateLimiter(
        "filebroker-auth",
        RateLimiterConfig
            .custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(15))
            .build()
    )

    var currentLogin: Login? = currentLogin
        get() = field
        set(value) {
            loginChangeCallback?.invoke(value)
            field = value
        }

    val loginRefreshLock = Mutex()

    @Serializable
    class Login(val token: String, val refreshToken: String, val expiry: Instant, val user: User)

    @Serializable
    class User(
        val user_name: String,
        val email: String? = null,
        val avatar_url: String? = null,
        val creation_timestamp: String
    )

    @Serializable
    class LoginRequest(val user_name: String, val password: String)

    @Serializable
    class LoginResponse(
        val token: String,
        val refresh_token: String,
        val expiration_secs: Int,
        val user: User
    )

    @Serializable
    class Post(
        val pk: Long,
        val data_url: String? = null,
        val source_url: String? = null,
        val title: String? = null,
        val creation_timestamp: String,
        val create_user: User,
        val score: Int,
        val s3_object: S3Object,
        val s3_object_metadata: S3ObjectMetadata,
        val thumbnail_url: String? = null,
        val thumbnail_object_key: String? = null,
        val is_public: Boolean,
        val description: String?
    ) {
        constructor(postDetailed: PostDetailed) : this(
            postDetailed.pk,
            postDetailed.data_url,
            postDetailed.source_url,
            postDetailed.title,
            postDetailed.creation_timestamp,
            postDetailed.create_user,
            postDetailed.score,
            postDetailed.s3_object,
            postDetailed.s3_object_metadata,
            postDetailed.thumbnail_url,
            postDetailed.s3_object.thumbnail_object_key,
            postDetailed.is_public,
            postDetailed.description,
        )
    }

    @Serializable
    class PostCollection(
        val pk: Long,
        val title: String? = null,
        val creation_timestamp: String,
        val create_user: User,
        val poster_object: S3Object? = null,
        val thumbnail_object_key: String? = null,
        val is_public: Boolean,
        val description: String?
    ) {
        constructor(postCollectionDetailed: PostCollectionDetailed) : this(
            postCollectionDetailed.pk,
            postCollectionDetailed.title,
            postCollectionDetailed.creation_timestamp,
            postCollectionDetailed.create_user,
            postCollectionDetailed.poster_object,
            postCollectionDetailed.poster_object?.thumbnail_object_key,
            postCollectionDetailed.is_public,
            postCollectionDetailed.description,
        )
    }

    @Serializable
    class PostCollectionDetailed(
        val pk: Long,
        val title: String? = null,
        val create_user: User,
        val creation_timestamp: String,
        val is_public: Boolean,
        val poster_object: S3Object? = null,
        val poster_object_key: String? = null,
        val description: String? = null,
        val is_editable: Boolean,
        val is_deletable: Boolean,
        val tags: List<Tag>,
        val group_access: List<PostCollectionGroupAccessDetailed>,
    )

    @Serializable
    class PostCollectionItem(
        val post: Post,
        val post_collection: PostCollection,
        val added_by: User,
        val creation_timestamp: String,
        val pk: Long,
        val ordinal: Int,
    )

    @Serializable
    class SearchResult(
        val full_count: Long? = null,
        val pages: Long? = null,
        val posts: List<Post>? = null,
        val collections: List<PostCollection>? = null,
        val collection_items: List<PostCollectionItem>? = null,
    )

    @Serializable
    class PostDetailed(
        val pk: Long,
        val data_url: String? = null,
        val source_url: String? = null,
        val title: String? = null,
        val creation_timestamp: String,
        val create_user: User,
        val score: Int,
        val s3_object: S3Object,
        val s3_object_metadata: S3ObjectMetadata,
        val thumbnail_url: String? = null,
        val prev_post: PostWindowObject? = null,
        val next_post: PostWindowObject? = null,
        val is_public: Boolean,
        val description: String? = null,
        val is_editable: Boolean,
        val is_deletable: Boolean,
        val tags: List<Tag>,
        val group_access: List<PostGroupAccessDetailed>
    )

    @Serializable
    class PostWindowObject(
        val pk: Long,
        val page: Long,
    )

    @Serializable
    class S3Object(
        val object_key: String,
        val sha256_hash: String? = null,
        val size_bytes: Long,
        val mime_type: String,
        val fk_broker: Int,
        val fk_uploader: Int,
        val thumbnail_object_key: String? = null,
        val creation_timestamp: String,
        val filename: String? = null,
        val hls_master_playlist: String? = null,
        val hls_disabled: Boolean,
        val hls_locked_at: String? = null,
        val thumbnail_locked_at: String? = null,
        val hls_fail_count: Int? = null,
        val thumbnail_fail_count: Int? = null,
        val thumbnail_disabled: Boolean? = null,
    )

    @Serializable
    class S3ObjectMetadata(
        val object_key: String,
        val file_type: String? = null,
        val file_type_extension: String? = null,
        val mime_type: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val album_artist: String? = null,
        val composer: String? = null,
        val genre: String? = null,
        val date: String? = null,
        val track_number: Int? = null,
        val disc_number: Int? = null,
        val duration: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val size: Long? = null,
        val bit_rate: Long? = null,
        val format_name: String? = null,
        val format_long_name: String? = null,
        val video_stream_count: Int,
        val video_codec_name: String? = null,
        val video_codec_long_name: String? = null,
        val video_frame_rate: Double? = null,
        val video_bit_rate_max: Long? = null,
        val audio_stream_count: Int,
        val audio_codec_name: String? = null,
        val audio_codec_long_name: String? = null,
        val audio_sample_rate: Double? = null,
        val audio_channels: Int? = null,
        val audio_bit_rate_max: Long? = null,
        val loaded: Boolean,
        val track_count: Int? = null,
        val disc_count: Int? = null,
    )

    @Serializable
    class Tag(
        val pk: Long,
        val tag_name: String,
        val creation_timestamp: String
    )

    @Serializable
    class PostGroupAccessDetailed(
        val fk_post: Long,
        val write: Boolean,
        val fk_granted_by: Int,
        val creation_timestamp: String,
        val granted_group: UserGroup
    )

    @Serializable
    class PostCollectionGroupAccessDetailed(
        val fk_post_collection: Long,
        val write: Boolean,
        val fk_granted_by: Long,
        val creation_timestamp: String,
        val granted_group: UserGroup,
    )

    @Serializable
    class UserGroup(
        val pk: Long,
        val name: String,
        val is_public: Boolean,
        val hidden: Boolean,
        val fk_owner: Int,
        val creation_timestamp: String
    )

    private suspend fun rateLimitDelay(rateLimiter: RateLimiter) {
        val delay = rateLimiter.reservePermission()

        if (delay == 0L) {
            return
        } else if (delay > 0) {
            delay(delay)
        } else {
            throw RateLimitException(false, "Rate limit exceeded for " + rateLimiter.name)
        }
    }

    fun loginAsync(request: LoginRequest): CompletableFuture<LoginResponse> = GlobalScope.future { login(request) }

    @Throws(Exception::class)
    suspend fun login(request: LoginRequest): LoginResponse {
        rateLimitDelay(authRateLimiter)

        val response = http.post(baseUrl + "login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status.isSuccess()) {
            val loginResponse = response.body<LoginResponse>()
            handleLoginResponse(loginResponse)

            return loginResponse
        } else if (response.status.value == 401) {
            throw InvalidCredentialsException(response.bodyAsText())
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    fun handleLoginResponse(loginResponse: LoginResponse): Login {
        val expirationSecs = loginResponse.expiration_secs / 3 * 2
        val now = Clock.System.now()
        val login = Login(
            loginResponse.token,
            loginResponse.refresh_token,
            now.plus(expirationSecs.seconds),
            loginResponse.user
        )
        currentLogin =
            login
        return login
    }

    suspend fun getCurrentLogin(): Login? {
        val currentLogin = this.currentLogin
        return if (currentLogin != null && currentLogin.expiry < Clock.System.now().plus(10.seconds)) {
            loginRefreshLock.withLock {
                // recheck after acquiring lock
                val currentLogin = this.currentLogin
                if (currentLogin != null && currentLogin.expiry < Clock.System.now().plus(10.seconds)) {
                    rateLimitDelay(rateLimiter)
                    val response =
                        http.post(baseUrl + "refresh-token/" + currentLogin.refreshToken)

                    if (response.status.isSuccess()) {
                        try {
                            handleLoginResponse(response.body())
                        } catch (e: Exception) {
                            logger.error("Failed to refresh login with exception", e)
                            currentLogin
                        }
                    } else if (response.status.value == 401) {
                        logger.warn("Failed to refresh login with status 401")
                        this.currentLogin = null
                        null
                    } else {
                        logger.error("Failed to refresh login with status ${response.status.value}")
                        currentLogin
                    }
                } else {
                    currentLogin
                }
            }
        } else {
            currentLogin
        }
    }

    suspend fun refreshLogin(refreshToken: String): Login? {
        return loginRefreshLock.withLock {
            rateLimitDelay(rateLimiter)
            val response =
                http.post(baseUrl + "refresh-token/" + refreshToken)

            if (response.status.isSuccess()) {
                try {
                    handleLoginResponse(response.body())
                } catch (e: Exception) {
                    logger.error("Failed to refresh login with exception", e)
                    null
                }
            } else {
                logger.error("Failed to refresh login with status ${response.status.value}")
                null
            }
        }
    }

    fun searchPostsAsync(
        query: String? = null,
        page: Long? = null,
        limit: Int? = null
    ): CompletableFuture<SearchResult> =
        GlobalScope.future { searchPosts(query, page, limit) }

    @Throws(Exception::class)
    suspend fun searchPosts(query: String? = null, page: Long? = null, limit: Int? = null): SearchResult {
        return search("post", query, page, limit)
    }

    fun searchPostCollectionsAsync(
        query: String? = null,
        page: Long? = null,
        limit: Int? = null
    ): CompletableFuture<SearchResult> =
        GlobalScope.future { searchPostCollections(query, page, limit) }

    @Throws(Exception::class)
    suspend fun searchPostCollections(query: String? = null, page: Long? = null, limit: Int? = null): SearchResult {
        return search("collection", query, page, limit)
    }

    fun searchPostCollectionItemsAsync(
        collectionPk: Long,
        query: String? = null,
        page: Long? = null,
        limit: Int? = null
    ): CompletableFuture<SearchResult> =
        GlobalScope.future { searchPostCollectionItems(collectionPk, query, page, limit) }

    @Throws(Exception::class)
    suspend fun searchPostCollectionItems(
        collectionPk: Long,
        query: String? = null,
        page: Long? = null,
        limit: Int? = null
    ): SearchResult {
        return search("collection/$collectionPk", query, page, limit)
    }

    suspend fun search(scope: String, query: String? = null, page: Long? = null, limit: Int? = null): SearchResult {
        val currentLogin = getCurrentLogin()
        rateLimitDelay(searchRateLimiter)
        val response = http.get(baseUrl + "search/" + scope) {
            if (query != null) {
                parameter("query", query)
            }
            if (page != null) {
                parameter("page", page)
            }
            if (limit != null) {
                parameter("limit", limit)
            }
            if (currentLogin != null) {
                header("Authorization", "Bearer " + currentLogin.token)
            }
        }

        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    fun getPostAsync(key: Long, query: String? = null, page: Long? = null): CompletableFuture<PostDetailed> =
        GlobalScope.future { getPost(key, query, page) }

    @Throws(Exception::class)
    suspend fun getPost(key: Long, query: String? = null, page: Long? = null): PostDetailed {
        val currentLogin = getCurrentLogin()
        rateLimitDelay(rateLimiter)
        val response = http.get(baseUrl + "get-post/" + key) {
            if (query != null) {
                parameter("query", query)
            }
            if (page != null) {
                parameter("page", page)
            }
            if (currentLogin != null) {
                header("Authorization", "Bearer " + currentLogin.token)
            }
        }

        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    fun getPostCollectionItemAsync(
        collectionKey: Long,
        postKey: Long,
        query: String? = null,
        page: Long? = null
    ): CompletableFuture<PostDetailed> =
        GlobalScope.future { getPostCollectionItem(collectionKey, postKey, query, page) }

    @Throws(Exception::class)
    suspend fun getPostCollectionItem(
        collectionKey: Long,
        postKey: Long,
        query: String? = null,
        page: Long? = null
    ): PostDetailed {
        val currentLogin = getCurrentLogin()
        rateLimitDelay(rateLimiter)
        val response = http.get(baseUrl + "get-post/" + collectionKey + "/" + postKey) {
            if (query != null) {
                parameter("query", query)
            }
            if (page != null) {
                parameter("page", page)
            }
            if (currentLogin != null) {
                header("Authorization", "Bearer " + currentLogin.token)
            }
        }

        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    fun getPostsAsync(keys: List<Long>): CompletableFuture<List<PostDetailed>> = GlobalScope.future { getPosts(keys) }

    @Throws(Exception::class)
    suspend fun getPosts(keys: List<Long>): List<PostDetailed> {
        val currentLogin = getCurrentLogin()
        rateLimitDelay(rateLimiter)
        val response = http.get(baseUrl + "get-posts/" + keys.joinToString(",")) {
            if (currentLogin != null) {
                header("Authorization", "Bearer " + currentLogin.token)
            }
        }

        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    fun getPostCollectionAsync(key: Long): CompletableFuture<PostCollectionDetailed> =
        GlobalScope.future { getPostCollection(key) }

    @Throws(Exception::class)
    suspend fun getPostCollection(key: Long): PostCollectionDetailed {
        val currentLogin = getCurrentLogin()
        rateLimitDelay(rateLimiter)
        val response = http.get(baseUrl + "get-collection/" + key) {
            if (currentLogin != null) {
                header("Authorization", "Bearer " + currentLogin.token)
            }
        }

        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw InvalidHttpResponseException(response.status.value, response.bodyAsText())
        }
    }

    open class InvalidHttpResponseException(val status: Int, val body: String) :
        RuntimeException("Received invalid status code $status, see response body for details")

    class InvalidCredentialsException(body: String) : InvalidHttpResponseException(401, body)
}

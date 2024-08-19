package net.robinfriedli.aiode.filebroker

import com.google.common.collect.Lists
import net.robinfriedli.aiode.util.BulkOperationService
import net.robinfriedli.filebroker.FilebrokerApi
import org.apache.commons.lang3.tuple.Pair
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Function

class FilebrokerPostBulkLoadingService(private val filebrokerApi: FilebrokerApi) :
    BulkOperationService<Long, FilebrokerApi.PostDetailed>(
        100,
        Function { pks ->
            val logger: Logger = LoggerFactory.getLogger(FilebrokerPostBulkLoadingService::class.java)
            val posts = try {
                filebrokerApi.getPostsAsync(pks).get(10, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                if (e.cause is FilebrokerApi.InvalidHttpResponseException) {
                    val filebrokerApiException = e.cause as FilebrokerApi.InvalidHttpResponseException
                    try {
                        val errorObject = JSONObject(filebrokerApiException.body)
                        if (errorObject.getInt("error_code") == 403_003 && errorObject.has("inaccessible_object_keys")) {
                            val inaccessibleKeys = errorObject.getJSONArray("inaccessible_object_keys")
                            for (i in 0 until inaccessibleKeys.length()) {
                                pks.remove(inaccessibleKeys.getLong(i))
                            }
                            filebrokerApi.getPostsAsync(pks).get(10, TimeUnit.SECONDS)
                        } else {
                            throw e
                        }
                    } catch (e: JSONException) {
                        logger.error("Failed to deserialize error response body", e)
                        throw e
                    }
                } else {
                    throw e
                }
            }
            return@Function posts.map { post -> Pair.of(post.pk, post) }
        }
    ) {
    override fun perform() {
        val batches: List<List<Long>> = Lists.partition(keys, size)
        for (batch in batches) {
            val loadedBatch: List<Pair<Long, FilebrokerApi.PostDetailed>> = loadFunc.apply(batch)
            for (keyValuePair in loadedBatch) {
                val key = keyValuePair.left
                val value = keyValuePair.right
                // as opposed to the spotify api, the filebroker api does not return the same post multiple times if the
                // same key is included multiple times in the request, thus the same post should be applied to all mapped
                // consumers
                val resultConsumer = actionMap[key]!!
                while (resultConsumer.hasNext()) {
                    resultConsumer.next().accept(value)
                }
            }
        }
    }
}

package labs.dx.readr.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import labs.dx.readr.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class OpenRouterSummarizer @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey().isBlank()) {
            return@withContext Result.failure(IllegalStateException(MissingApiKeyMessage))
        }
        if (text.isBlank()) {
            return@withContext Result.failure(IllegalStateException("There is no extracted text to summarize."))
        }

        runCatching {
            val payload = JSONObject()
                .put("model", Model)
                .put("messages", JSONArray().apply {
                    put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "$Prompt\n\nAttached book/text:\n${text.toModelInput()}")
                    )
                })
                .put("temperature", 0.2)
                .put("max_tokens", 450)

            val request = Request.Builder()
                .url(Endpoint)
                .header("Authorization", "Bearer ${apiKey()}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("OpenRouter failed: ${response.code} ${responseBody.toApiErrorMessage()}")
                }
                JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .ifBlank { throw IOException("OpenRouter returned an empty summary.") }
            }
        }
    }

    private fun apiKey(): String = BuildConfig.OPENROUTER_API_KEY

    private fun String.toModelInput(): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        return normalized.take(MaxInputChars)
    }

    private fun String.toApiErrorMessage(): String {
        val parsed = runCatching { JSONObject(this) }.getOrNull()
        val error = parsed?.optJSONObject("error")
        return error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: take(280).ifBlank { "No response body" }
    }

    private companion object {
        const val Endpoint = "https://openrouter.ai/api/v1/chat/completions"
        const val Model = "openai/gpt-oss-20b:free"
        const val MaxInputChars = 80_000
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val MissingApiKeyMessage =
            "OpenRouter API key missing. Add OPENROUTER_API_KEY to local.properties, then rebuild the app."
        const val Prompt =
            "Act as an expert in business and literature. Read the attached book/text and generate a 200-word executive brief. Use headings and bullet points. Lead with the core thesis, followed by the 3 main ideas, and 3 practical actions. Use tight, clear language"
    }
}

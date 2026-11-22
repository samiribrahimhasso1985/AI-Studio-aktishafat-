package com.iktishafat.teleprompter.data.ai

import com.iktishafat.teleprompter.domain.assistant.AssistantResult
import com.iktishafat.teleprompter.domain.assistant.AssistantTask
import com.iktishafat.teleprompter.domain.assistant.LocalScriptAssistant
import com.iktishafat.teleprompter.domain.assistant.ScriptAssistant
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional cloud assistant using the Anthropic Messages API with the user's own API
 * key. Disabled by default; nothing leaves the device unless the user enables it.
 */
class CloudScriptAssistant(
    private val apiKey: String,
    private val model: String,
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
) : ScriptAssistant {

    override suspend fun run(task: AssistantTask, text: String, language: ScriptLanguage): AssistantResult =
        withContext(Dispatchers.IO) {
            val langName = when (language) {
                ScriptLanguage.ARABIC -> "Arabic (Modern Standard Arabic unless the script uses a dialect)"
                ScriptLanguage.GERMAN -> "German"
                ScriptLanguage.ENGLISH -> "English"
            }
            val pause = if (language == ScriptLanguage.ARABIC) "[وقفة]" else "[PAUSE]"
            val breath = if (language == ScriptLanguage.ARABIC) "[نفس]" else "[BREATH]"
            val instruction = when (task) {
                AssistantTask.IMPROVE_FOR_SPEECH ->
                    "Rewrite the script so it is natural to read aloud from a teleprompter: short sentences, " +
                        "one sentence per line, spoken rhythm, numbers and symbols written as spoken words. " +
                        "Keep every fact, name and the author's meaning. Do not add new information."
                AssistantTask.ADD_PAUSES ->
                    "Insert natural pause markers into the script without changing any words. Use $pause for a " +
                        "one-second pause and $breath for a short breath. Put them where a professional presenter would pause."
                AssistantTask.SPLIT_SCENES ->
                    "Divide the script into scenes of roughly 30–60 seconds of speech each. Start each scene with a " +
                        "line of the form '## <Scene title>'. Do not change the script's words."
            }
            val system = "You are a script editor for video presenters. Answer only with the finished script text " +
                "in $langName, no explanations, no code fences. Lines starting with '##' are scene headings."

            val body = JSONObject()
                .put("model", model.ifBlank { PrompterSettings.DEFAULT_AI_MODEL })
                .put("max_tokens", 8000)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "$instruction\n\n<script>\n$text\n</script>")))

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
                    throw IOException("AI service error $code${msg?.let { ": $it" } ?: ""}")
                }
                val content = JSONObject(response).getJSONArray("content")
                val out = StringBuilder()
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.optString("type") == "text") out.append(block.getString("text"))
                }
                val result = out.toString().trim().removePrefix("<script>").removeSuffix("</script>").trim()
                if (result.isEmpty()) throw IOException("Empty response from AI service")
                AssistantResult(result, listOf(model), usedCloud = true)
            } finally {
                conn.disconnect()
            }
        }
}

/** Uses the cloud assistant only when the user enabled it and supplied a key. */
class AssistantRouter(private val local: LocalScriptAssistant = LocalScriptAssistant()) {
    fun pick(settings: PrompterSettings): ScriptAssistant =
        if (settings.aiCloudEnabled && settings.aiApiKey.isNotBlank()) CloudScriptAssistant(settings.aiApiKey, settings.aiModel)
        else local
}

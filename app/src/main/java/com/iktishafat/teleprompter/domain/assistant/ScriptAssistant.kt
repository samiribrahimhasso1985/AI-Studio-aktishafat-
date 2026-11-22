package com.iktishafat.teleprompter.domain.assistant

import com.iktishafat.teleprompter.domain.model.ScriptLanguage

enum class AssistantTask { IMPROVE_FOR_SPEECH, ADD_PAUSES, SPLIT_SCENES }

data class AssistantResult(val text: String, val notes: List<String>, val usedCloud: Boolean = false)

/** Rewrites scripts for spoken delivery. Implementations: offline rules or cloud LLM. */
interface ScriptAssistant {
    suspend fun run(task: AssistantTask, text: String, language: ScriptLanguage): AssistantResult
}

/**
 * Fully offline, deterministic assistant. It does not "write" new content; it
 * restructures what is there so it is easier to read aloud from a prompter.
 */
class LocalScriptAssistant(private val sceneTargetSeconds: Int = 45) : ScriptAssistant {

    override suspend fun run(task: AssistantTask, text: String, language: ScriptLanguage): AssistantResult =
        when (task) {
            AssistantTask.IMPROVE_FOR_SPEECH -> improveForSpeech(text, language)
            AssistantTask.ADD_PAUSES -> addPauses(text, language)
            AssistantTask.SPLIT_SCENES -> splitIntoScenes(text, language)
        }

    private val conjunctions = mapOf(
        ScriptLanguage.ARABIC to setOf("ثم", "لكن", "لكنّ", "لذلك", "بينما", "حيث", "بل", "إذ"),
        ScriptLanguage.GERMAN to setOf("und", "aber", "denn", "weil", "doch", "sondern", "während", "deshalb"),
        ScriptLanguage.ENGLISH to setOf("and", "but", "because", "so", "which", "while", "therefore"),
    )
    private val contrastWords = mapOf(
        ScriptLanguage.ARABIC to setOf("لكن", "لكنّ", "ولكن", "غير", "بينما", "إلا"),
        ScriptLanguage.GERMAN to setOf("aber", "doch", "jedoch", "trotzdem", "allerdings"),
        ScriptLanguage.ENGLISH to setOf("but", "however", "yet", "still", "although"),
    )

    private fun pauseTag(language: ScriptLanguage) = if (language == ScriptLanguage.ARABIC) "[وقفة]" else "[PAUSE]"
    private fun breathTag(language: ScriptLanguage) = if (language == ScriptLanguage.ARABIC) "[نفس]" else "[BREATH]"

    internal fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

    internal fun sentences(paragraph: String): List<String> {
        val flat = paragraph.replace(Regex("\\s*\\n\\s*"), " ")
        val out = ArrayList<String>()
        val re = Regex("[^.!?؟…]+[.!?؟…]*")
        re.findAll(flat).forEach { val s = it.value.trim(); if (s.isNotEmpty()) out.add(s) }
        return out
    }

    private fun wordCount(s: String) = s.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Splits a sentence longer than [maxWords] at a comma or conjunction near its middle. */
    internal fun splitLong(sentence: String, language: ScriptLanguage, maxWords: Int = 22): List<String> {
        val words = sentence.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= maxWords) return listOf(sentence)
        val mid = words.size / 2
        val conj = conjunctions[language].orEmpty()
        var best = -1
        var bestDist = Int.MAX_VALUE
        for (i in 3 until words.size - 3) {
            val w = words[i]
            val prevComma = words[i - 1].endsWith(",") || words[i - 1].endsWith("،") || words[i - 1].endsWith(";")
            val isConj = w.lowercase().trim(',', '،') in conj
            if (prevComma || isConj) {
                val d = kotlin.math.abs(i - mid)
                if (d < bestDist) { bestDist = d; best = i }
            }
        }
        if (best < 0) return listOf(sentence)
        val endPunct = if (language == ScriptLanguage.ARABIC) "." else "."
        val first = words.subList(0, best).joinToString(" ").trimEnd(',', '،', ';') + endPunct
        val rest = words.subList(best, words.size).joinToString(" ")
        val second = rest.replaceFirstChar { it.uppercaseChar() }
        return splitLong(first, language, maxWords) + splitLong(second, language, maxWords)
    }

    fun improveForSpeech(text: String, language: ScriptLanguage): AssistantResult {
        val notes = ArrayList<String>()
        var t = text.replace("\t", " ").replace(Regex("[ \\u00A0]{2,}"), " ")
        val parens = Regex("\\s*\\(([^()]{1,80})\\)")
        if (parens.containsMatchIn(t)) {
            t = parens.replace(t) { m -> if (language == ScriptLanguage.ARABIC) "، ${m.groupValues[1]}،" else ", ${m.groupValues[1]}," }
            notes += "parentheses→commas"
        }
        val percent = when (language) { ScriptLanguage.ARABIC -> " بالمئة"; ScriptLanguage.GERMAN -> " Prozent"; ScriptLanguage.ENGLISH -> " percent" }
        val and = when (language) { ScriptLanguage.ARABIC -> " و "; ScriptLanguage.GERMAN -> " und "; ScriptLanguage.ENGLISH -> " and " }
        if (t.contains('%') || t.contains(" & ")) notes += "symbols→words"
        t = t.replace(Regex("(\\d)\\s*%"), "$1$percent").replace(" & ", and)

        var splitCount = 0
        val result = paragraphs(t).joinToString("\n\n") { p ->
            if (p.startsWith("#")) p else sentences(p).flatMap { s ->
                val parts = splitLong(s, language)
                if (parts.size > 1) splitCount += parts.size - 1
                parts
            }.joinToString("\n")
        }
        if (splitCount > 0) notes += "split $splitCount long sentence(s)"
        notes += "one sentence per line"
        return AssistantResult(result, notes)
    }

    fun addPauses(text: String, language: ScriptLanguage): AssistantResult {
        val pause = pauseTag(language)
        val breath = breathTag(language)
        val contrast = contrastWords[language].orEmpty()
        var added = 0
        val paras = paragraphs(text)
        val out = paras.mapIndexed { idx, p ->
            if (p.startsWith("#")) return@mapIndexed p
            val lines = p.split("\n").map { line ->
                val first = line.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.trim(',', '،') ?: ""
                if (first in contrast && !line.contains(breath)) { added++; "$breath $line" } else line
            }.toMutableList()
            var body = lines.joinToString("\n")
            body = body.replace(Regex("([?؟])\\s+(?!\\[)")) { m -> added++; "${m.groupValues[1]} $breath " }
            val isLast = idx == paras.lastIndex
            if (!isLast && !body.trimEnd().endsWith("]")) { added++; body = "$body $pause" }
            body
        }.joinToString("\n\n")
        return AssistantResult(out, listOf("added $added pause marker(s)"))
    }

    fun splitIntoScenes(text: String, language: ScriptLanguage): AssistantResult {
        val paras = paragraphs(text)
        if (paras.count { it.startsWith("#") } >= 2) return AssistantResult(text, listOf("already has scenes"))
        val label = when (language) { ScriptLanguage.ARABIC -> "المشهد"; ScriptLanguage.GERMAN -> "Szene"; ScriptLanguage.ENGLISH -> "Scene" }
        val scenes = ArrayList<MutableList<String>>()
        var current = mutableListOf<String>()
        var seconds = 0
        for (p in paras.filterNot { it.startsWith("#") }) {
            val d = SpeechDurationEstimator.estimate(p, language).totalSeconds
            if (current.isNotEmpty() && seconds + d > sceneTargetSeconds * 1.25) {
                scenes += current; current = mutableListOf(); seconds = 0
            }
            current += p; seconds += d
            if (seconds >= sceneTargetSeconds) { scenes += current; current = mutableListOf(); seconds = 0 }
        }
        if (current.isNotEmpty()) scenes += current
        val out = scenes.mapIndexed { i, s ->
            val dur = SpeechDurationEstimator.estimate(s.joinToString("\n\n"), language)
            "## $label ${i + 1} · ${dur.formatted}\n\n" + s.joinToString("\n\n")
        }.joinToString("\n\n")
        return AssistantResult(out, listOf("${scenes.size} scene(s), ~${sceneTargetSeconds}s each"))
    }
}

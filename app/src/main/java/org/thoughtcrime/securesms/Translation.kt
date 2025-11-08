/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.model.MessageId
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Translation {
  val TTS_LangStateFlow: MutableLiveData<String?> = MutableLiveData(null)
  val languages = arrayOf("France", "Canadian", "English", "Arabic")
  val languagesCodes = arrayOf("fr", "en-CA", "en", "ar")
  val isLoading = MutableStateFlow<MessageId?>(null)


  suspend fun translateMessage(message: String, openAiKey: String, fromLang: String, toLang: String = "English"): TranslateResult {
    return try {
      withContext(Dispatchers.IO) {
        val url = URL("https://api.openai.com/v1/responses")
        val instruction = """
          You are a bilingual assistant for $fromLang learners. 

          Your task is to translate a given $fromLang sentence into $toLang **word by word**,
          preserving the **original $fromLang word order** and displaying both languages in two aligned rows.

          ### Output Format:
          - First row: the $fromLang flag, followed by each $fromLang word enclosed in square brackets, separated by spaces.  
          - Second row: the $toLang flag, followed by the corresponding $toLang translations (literal and common meanings), also enclosed in square brackets and vertically aligned under their $fromLang counterparts for readability.

          ### Rules:
          - Keep punctuation and numbers exactly as in the original sentence.
          - Use the most common, literal meaning of each word in context.
          - Do **not** reorder words to make the $toLang grammatically correct.
          - Strive for visual alignment so that each $fromLang–$toLang pair lines up neatly.
          - Enclose all words in square brackets `[]`.

          ### Example
          **Input (French):**  
          Je pense faire.

          **Output:**  
          🇫🇷 [Je] [pense] [faire]  
          🇬🇧 [I ] [think] [to do]

        """.trimIndent()
        val payload = org.json.JSONObject().apply {
          put("model", "gpt-4.1")
          put("instructions", instruction)
          put("input", message)
          put("store", true)
        }

        with(url.openConnection() as HttpURLConnection) {
          requestMethod = "POST"
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Authorization", "Bearer $openAiKey")
          doOutput = true
          OutputStreamWriter(outputStream).use { writer ->
            writer.write(payload.toString())
          }
          if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = inputStream.bufferedReader().use(BufferedReader::readText)
            val outputText = org.json.JSONObject(response)
              .getJSONArray("output")
              .getJSONObject(0)
              .getJSONArray("content")
              .getJSONObject(0)
              .getString("text")

            TranslateResult.Success(processTranslation(outputText))
          } else {
            val response = errorStream?.bufferedReader()?.use(BufferedReader::readText)
            TranslateResult.Error("Translation failed with response code $responseCode: $response")
          }
        }
      }
    } catch (e: Exception) {
      TranslateResult.Error("Translation exception: ${e.message}")
    }
  }

  private fun processTranslation(text: String): String {
    return try {
      val lines = text.lines().filter { it.isNotBlank() }
      if (lines.size < 2) return text

      val flag1 = lines[0].substringBefore(' ').takeIf { it.isNotBlank() } ?: return text
      val flag2 = lines[1].substringBefore(' ').takeIf { it.isNotBlank() } ?: return text
      val part1 = lines[0]
      val part2 = lines[1]

      fun extractWords(part: String): List<String> {
        val words = mutableListOf<String>()
        var currentWord = StringBuilder()
        var insideBrackets = false

        for (char in part) {
          when (char) {
            '[' -> {
              insideBrackets = true
              if (currentWord.isNotEmpty()) {
                words.add(currentWord.toString().trim())
                currentWord = StringBuilder()
              }
            }

            ']' -> {
              insideBrackets = false
              if (currentWord.isNotEmpty()) {
                words.add(currentWord.toString().trim())
                currentWord = StringBuilder()
              }
            }

            else -> {
              if (insideBrackets) currentWord.append(char)
            }
          }
        }
        return words
      }

      val wordsList1 = extractWords(part1)
      val wordsList2 = extractWords(part2)
      if (wordsList1.size != wordsList2.size) {
        throw Exception("Mismatched word counts between languages")
      }

      val res = StringBuilder()
      val temp1 = StringBuilder()
      val temp2 = StringBuilder()
      val maxLineLength = 25

      for (i in wordsList1.indices) {
        val w1 = wordsList1[i]
        val w2 = wordsList2[i] // fixed: use wordsList2

        if (temp1.length + w1.length >= maxLineLength || temp2.length + w2.length >= maxLineLength) {
          res.append(flag1).append(" ").append(temp1.toString().trim()).append("\n")
          res.append(flag2).append(" ").append(temp2.toString().trim())
          res.append("\n\n")
          temp1.setLength(0)
          temp2.setLength(0)
        }

        temp1.append("[").append(w1).append("] ")
        temp2.append("[").append(w2).append("] ")
      }

      if (temp1.isNotEmpty() || temp2.isNotEmpty()) {
        res.append(flag1).append(" ").append(temp1.toString().trim()).append("\n")
        res.append(flag2).append(" ").append(temp2.toString().trim()).append("\n")
      }

      res.toString().trim()
    } catch (e: Exception) {
      e.printStackTrace()
      text
    }
  }

  sealed class TranslateResult {
    data class Success(val translatedText: String) : TranslateResult()
    data class Error(val errorMessage: String) : TranslateResult()
  }
}
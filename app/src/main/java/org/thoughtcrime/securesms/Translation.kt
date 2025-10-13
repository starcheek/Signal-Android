/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Translation {
  val TTS_LangStateFlow: MutableLiveData<String?> = MutableLiveData(null)
  val languages = arrayOf("France", "Canadian", "English", "Arabic")
  val languagesCodes = arrayOf("fr", "en-CA", "en", "ar")


  suspend fun translateMessage(message: String,openAiKey:String): TranslateResult {
    return try {
      withContext(Dispatchers.IO) {


        val url = URL("https://api.openai.com/v1/responses")
        val instruction = "You are a bilingual assistant for French learners. Task: Translate a given French sentence into English word-by-word, keeping the French word order, and aligning the output in two rows: - First row: French words separated by a space. - Second row: Corresponding English words, padded with spaces so that they line up under their French counterpart. Rules: - Keep punctuation and numbers in place. - Use the most common, literal meaning of each French word in the given context. - Do not re-order the words to make English grammar correct. - Try to keep each French-English pair vertically aligned for readability. Example input: Je pense faire la tarte à la rhubarbe. Example output: [Je] [pense] [faire] \n[I ] [think] [to do] \n\n[la ] [tarte] [à] \n[the] [ pie ] [to] \n\n[la ] [rhubarbe] \n[the] [rhubarb ]."
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
            TranslateResult.Success(outputText)
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
 sealed class TranslateResult {
    data class Success(val translatedText: String) : TranslateResult()
    data class Error(val errorMessage: String) : TranslateResult()
  }
}
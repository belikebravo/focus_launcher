package com.example.focuslauncher.data.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiDataSource {

    private const val API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=%s"

    suspend fun fetchNuggetsForTopics(topics: List<Topic>, apiKey: String): List<KnowledgeNugget> = withContext(Dispatchers.IO) {
        val nuggets = mutableListOf<KnowledgeNugget>()
        try {
            val url = URL(String.format(API_URL_TEMPLATE, apiKey))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Prompt engineering for BATCH fetching (Interview Focus - Multi Topic)
            val topicListString = topics.joinToString(", ") { it.displayName }
            val prompt = "Generate 20 senior interview questions and answers for EACH of the following topics: [$topicListString]. " +
                    "For example, if there are 2 topics, generate 40 items total. " +
                    "Focus on depth, architectural patterns, and real-world scenarios. " +
                    "Format the response strictly as a JSON Object with a single key 'facts', which is an array of objects. " +
                    "Each object must have 'topic' (The Topic Name), 'title' (The Question, max 20 words) and 'content' (The Answer). " +
                    "Crucial: The 'content' (Answer) MUST be a COMPLETE, DETAILED explanation. Do not truncate. It should be long enough to require scrolling (approx 100-200 words). " +
                    "Do not use Markdown formatting in the JSON."

            val jsonBody = JSONObject()
            val contentsArray = org.json.JSONArray()
            val contentObj = JSONObject()
            val partsArray = org.json.JSONArray()
            val partObj = JSONObject()
            
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            jsonBody.put("contents", contentsArray)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                // Parse Response
                val respJson = JSONObject(response.toString())
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentParts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
                    if (contentParts != null && contentParts.length() > 0) {
                        val rawText = contentParts.getJSONObject(0).optString("text")
                        
                        try {
                            val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                            val rootObj = JSONObject(cleanJson)
                            val factsArray = rootObj.optJSONArray("facts")
                            
                            if (factsArray != null) {
                                for (i in 0 until factsArray.length()) {
                                    val factObj = factsArray.getJSONObject(i)
                                    val title = factObj.optString("title", "Question")
                                    val content = factObj.optString("content", "")
                                    val topicName = factObj.optString("topic", topics.firstOrNull()?.displayName ?: "General")
                                    
                                    // Find original topic object or create temp one
                                    val sourceTopic = topics.find { it.displayName.equals(topicName, ignoreCase = true) } 
                                        ?: Topic(topicName.uppercase().replace(" ", "_"), topicName)
                                    
                                    if (content.isNotBlank()) {
                                        nuggets.add(KnowledgeNugget(
                                            id = "gemini_${System.currentTimeMillis()}_$i",
                                            topic = sourceTopic,
                                            difficulty = Difficulty.INTERMEDIATE,
                                            shortText = title,
                                            detailedText = content
                                        ))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FocusGemini", "JSON Parse Error: ${e.message}")
                        }
                    }
                }
            } else {
                val errorMsg = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                } catch (e: Exception) { "Could not read error body" }
                android.util.Log.e("FocusGemini", "Gemini Error: $responseCode - $errorMsg")
                throw java.io.IOException("HTTP $responseCode: $errorMsg")
            }
            connection.disconnect()
        } catch (e: Exception) {
            // Propagate exception so ViewModel can handle 429 vs 403
            throw e
        }
        return@withContext nuggets
    }
}

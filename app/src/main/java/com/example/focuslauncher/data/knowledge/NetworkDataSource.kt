package com.example.focuslauncher.data.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object NetworkDataSource {

    // Using a raw JSON file from a reliable GitHub repository as our "API" for stability.
    // This allows "one time fetching" as requested.
    // Wikipedia API for dynamic knowledge
    private const val WIKI_API_BASE = "https://en.wikipedia.org/w/api.php"
    private const val WIKI_SUMMARY_BASE = "https://en.wikipedia.org/api/rest_v1/page/summary/"

    suspend fun fetchNuggetsForTopics(topics: List<Topic>): List<KnowledgeNugget> = withContext(Dispatchers.IO) {
        val nuggets = mutableListOf<KnowledgeNugget>()
        android.util.Log.d("FocusKnowledge", "Fetching nuggets for topics: ${topics.map { it.displayName }}")
        
        for (topic in topics) {
             try {
                // 1. Search for related pages
                val query = topic.displayName.replace(" ", "%20")
                // Fetch 10 results per topic as requested
                val searchUrlStr = "$WIKI_API_BASE?action=query&format=json&list=search&srlimit=10&srsearch=$query"
                android.util.Log.d("FocusKnowledge", "Search URL: $searchUrlStr")
                
                val searchConnection = URL(searchUrlStr).openConnection() as HttpURLConnection
                searchConnection.requestMethod = "GET"
                searchConnection.connectTimeout = 5000
                searchConnection.readTimeout = 5000
                
                val responseCode = searchConnection.responseCode
                android.util.Log.d("FocusKnowledge", "Search Response Code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val searchResponse = readResponse(searchConnection)
                    searchConnection.disconnect()
                    
                    val jsonResponse = org.json.JSONObject(searchResponse)
                    val searchResults = jsonResponse.getJSONObject("query").getJSONArray("search")
                    
                    android.util.Log.d("FocusKnowledge", "Found ${searchResults.length()} results for ${topic.displayName}")
                    
                    // 2. Fetch summary for each result
                    for (i in 0 until searchResults.length()) {
                        val result = searchResults.getJSONObject(i)
                        val title = result.getString("title")
                        val pageId = result.getInt("pageid")
                        
                        // Fetch Summary
                        val summaryUrlStr = "$WIKI_SUMMARY_BASE${title.replace(" ", "_")}"
                        val summaryConnection = URL(summaryUrlStr).openConnection() as HttpURLConnection
                        summaryConnection.requestMethod = "GET"
                        
                        if (summaryConnection.responseCode == HttpURLConnection.HTTP_OK) {
                            val summaryResponse = readResponse(summaryConnection)
                            val summaryJson = org.json.JSONObject(summaryResponse)
                            
                            val extract = summaryJson.optString("extract", "No description available.")
                            val description = summaryJson.optString("description", topic.displayName)
                            var thumbnail = summaryJson.optJSONObject("thumbnail")?.optString("source")
                            
                            if (extract.length > 50) { // Filter out very short/bad results
                                nuggets.add(
                                    KnowledgeNugget(
                                        id = "wiki_$pageId",
                                        topic = topic,
                                        difficulty = Difficulty.INTERMEDIATE,
                                        shortText = description ?: title,
                                        detailedText = extract
                                    )
                                )
                            }
                        } else {
                             android.util.Log.e("FocusKnowledge", "Summary fetch failed for $title: ${summaryConnection.responseCode}")
                        }
                        summaryConnection.disconnect()
                    }
                } else {
                    searchConnection.disconnect()
                    android.util.Log.e("FocusKnowledge", "Search failed: $responseCode")
                }
             } catch (e: Exception) {
                 e.printStackTrace()
                 android.util.Log.e("FocusKnowledge", "Exception fetching for ${topic.displayName}", e)
             }
        }
        android.util.Log.d("FocusKnowledge", "Total nuggets fetched: ${nuggets.size}")
        return@withContext nuggets
    }
    
    // Legacy method for compatibility if needed, but we should switch to the one above.
    suspend fun fetchQuotesAndMapToNuggets(): List<KnowledgeNugget> {
        return emptyList() 
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        return response.toString()
    }
}

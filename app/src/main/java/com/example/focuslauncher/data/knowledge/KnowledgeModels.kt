package com.example.focuslauncher.data.knowledge

// Changed from enum to allow dynamic topics
data class Topic(val id: String, val displayName: String) {
// Hardcoded topics removed.
}

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

data class KnowledgeNugget(
    val id: String,
    val topic: Topic,
    val difficulty: Difficulty,
    val shortText: String, // One-liner
    val detailedText: String // Paragraph
)

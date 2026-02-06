package com.example.focuslauncher.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslauncher.data.knowledge.QuizQuestion

data class QuizState(
    val selectedOptionIndex: Int? = null,
    val isAnswered: Boolean = false,
    val isCorrect: Boolean = false
)

@Composable
fun QuizCard(
    quiz: QuizQuestion,
    uiState: QuizState,
    onOptionSelected: (Int) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Text(
            text = quiz.topic.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = quiz.question,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        quiz.options.forEachIndexed { index, option ->
            val isSelected = uiState.selectedOptionIndex == index
            val isCorrect = index == quiz.correctAnswerIndex
            
            // Determine Color
            val backgroundColor = if (uiState.isAnswered) {
                when {
                    isCorrect -> Color(0xFF4CAF50) // Green
                    isSelected && !isCorrect -> Color(0xFFF44336) // Red
                    else -> Color(0xFF333333)
                }
            } else {
                if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF333333)
            }
            
            Button(
                onClick = { onOptionSelected(index) },
                enabled = !uiState.isAnswered, // Disable interaction once answered
                colors = ButtonDefaults.buttonColors(
                    containerColor = backgroundColor,
                    disabledContainerColor = backgroundColor,
                    disabledContentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                 Text(
                     text = option,
                     style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(8.dp),
                     textAlign = TextAlign.Center
                 )
            }
        }
        
        if (uiState.isAnswered) {
             Spacer(modifier = Modifier.height(16.dp))
             Card(
                 colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
             ) {
                 Column(modifier = Modifier.padding(16.dp)) {
                     Text(
                         text = if (uiState.isCorrect) "Correct!" else "Incorrect",
                         color = if (uiState.isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336), 
                         fontWeight = FontWeight.Bold
                     )
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(quiz.explanation, color = Color.LightGray)
                 }
             }
        }
    }
}

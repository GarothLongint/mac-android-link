package com.maclink.android.ui.call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maclink.android.ui.MacLinkTheme

/**
 * Pełnoekranowe okno "Odbierz / Odrzuć" które pojawia się na Samsung
 * gdy Mac wysyła polecenie odebrania połączenia.
 * Użytkownik musi tapnąć — Samsung blokuje programowe odbieranie.
 */
class AnswerCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_NUMBER = "caller_number"

        fun launch(context: Context, callerName: String, callerNumber: String) {
            val intent = Intent(context, AnswerCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pokaż na ekranie blokady
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: ""
        val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: ""
        val displayName = callerName.ifBlank { callerNumber.ifBlank { "Nieznany" } }

        setContent {
            MacLinkTheme {
                AnswerScreen(
                    displayName = displayName,
                    callerNumber = callerNumber,
                    onAnswer = { finish() },    // użytkownik sam odbiera z oryginalnego ekranu
                    onReject = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AnswerScreen(
    displayName: String,
    callerNumber: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF3F51B5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 40.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Połączenie z Maca",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Text(
                displayName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            if (callerNumber.isNotBlank() && callerNumber != displayName) {
                Text(callerNumber, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Wróć do ekranu połączenia aby odebrać",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp
            )

            Button(
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zamknij", fontSize = 16.sp)
            }
        }
    }
}

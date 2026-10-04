package com.example.mindnotes2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Модель відповіді від нашого FastAPI бекенду
data class NoteResponse(
    val title: String,
    val category: String,
    val formatType: String,
    val structuredContent: String,
    val hasReminder: Boolean,
    val reminderDatetime: String?,
    val reasoning: String
)

// Стани інтерфейсу за вимогами методички
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: NoteResponse) : UiState()
    data class Error(val message: String) : UiState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var inputText by remember { mutableStateOf("") }
    var uiState by remember { mutableStateOf<UiState>(UiState.Idle) }
    val scope = rememberCoroutineScope()

    fun sendRequest() {
        val trimmed = inputText.trim()
        if (trimmed.length < 3) {
            uiState = UiState.Error("Введіть щонайменше 3 символи")
            return
        }

        uiState = UiState.Loading
        scope.launch {
            try {
                val result = callBackendApi(trimmed)
                uiState = UiState.Success(result)
            } catch (e: Exception) {
                uiState = UiState.Error("Помилка з'єднання: ${e.localizedMessage ?: "Бекенд недоступний"}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MindNotes MVP (Lab 2)", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Поле введення
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Введіть сиру думку чи плани...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка відправки
        Button(
            onClick = { sendRequest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is UiState.Loading
        ) {
            Text("Обробити через AI Backend")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Відображення 4 станів інтерфейсу
        when (val state = uiState) {
            is UiState.Idle -> {
                Text("Введіть текст та натисніть кнопку для аналізу", color = Color.Gray)
            }
            is UiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Бекенд обробляє запит через Gemini...")
            }
            is UiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📌 Заголовок: ${state.data.title}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("🏷 Категорія: ${state.data.category}", color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("📝 Структурований текст:")
                        Text(state.data.structuredContent, fontWeight = FontWeight.Medium)

                        if (state.data.hasReminder && state.data.reminderDatetime != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("⏰ Нагадування: ${state.data.reminderDatetime}", color = Color(0xFFD32F2F))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("💡 Пояснення AI: ${state.data.reasoning}", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
            is UiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { sendRequest() }) {
                    Text("Повторити спробу")
                }
            }
        }
    }
}

// Функція виклику бекенду
suspend fun callBackendApi(rawText: String): NoteResponse = withContext(Dispatchers.IO) {
    // 10.0.2.2 — адреса localhost для Android Emulator
    val url = URL("http://127.0.0.1:8000/api/v1/notes/analyze")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connectTimeout = 5000
        readTimeout = 15000
        doOutput = true
    }

    val body = JSONObject().apply { put("text", rawText) }
    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }

    val code = conn.responseCode
    if (code == HttpURLConnection.HTTP_OK) {
        val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
        val json = JSONObject(responseStr)

        return@withContext NoteResponse(
            title = json.getString("title"),
            category = json.getString("category"),
            formatType = json.getString("format_type"),
            structuredContent = json.getString("structured_content"),
            hasReminder = json.getBoolean("has_reminder"),
            reminderDatetime = if (json.isNull("reminder_datetime")) null else json.getString("reminder_datetime"),
            reasoning = json.getString("reasoning")
        )
    } else {
        throw Exception("Код сервера: $code")
    }
}
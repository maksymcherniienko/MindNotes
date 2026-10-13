# Архітектура та потік даних проєкту

## 1. Mobile компоненти (Android / Jetpack Compose)

*   **UI Layer (`MainScreen`):**
    *   Текстове поле вводу сирої нотатки з валідацією (мінімум 3 символи).
    *   Реактивне відображення 4 станів інтерфейсу через `UiState`: `Idle`, `Loading`, `Success`, `Error`.
    *   Картка результату: відображення заголовка, категорії, структурованого контенту, нагадування та пояснення AI.
*   **Network / Integration Layer (`callBackendApi`):**
    *   HTTP-клієнт (`HttpURLConnection`) у корутині `Dispatchers.IO` з таймаутами (connect: 5s, read: 15s).
    *   Серіалізація тіла запиту `{"text": "..."}` у JSON та парсинг відповіді в модель `NoteResponse`.
*   **Error Handling & UX (`getReadableErrorMessage`):**
    *   Мапінг мережевих винятків (`ConnectException`, `SocketTimeoutException`, HTTP 422/500) у зрозумілі для користувача повідомлення з можливістю повтору (`Retry`).

---

## 2. Backend компоненти (FastAPI / Python)

*   **API Controller / Routing (`backend/main.py`):**
    *   `GET /health` — healthcheck сервісу.
    *   `POST /api/v1/notes/analyze` — основний ендпоінт прийому та обробки нотаток.
*   **Validation & DTOs (Pydantic моделі):**
    *   `NoteAnalyzeRequest`: валідація вхідного тексту (довжина 3–2000 символів, очищення від пробілів).
    *   `NoteAnalyzeResponse`: сувора типізація вихідного контракту (`title`, `category`, `format_type`, `structured_content`, `has_reminder`, `reminder_datetime`, `reasoning`).
*   **Service / Processing Pipeline:**
    *   Перевірка наявності API ключа (`GEMINI_API_KEY`).
    *   Маршрутизація запиту до хмарного AI або до локального fallback-рушія.

---

## 3. AI компоненти

*   **Primary AI Engine (Google Gemini 2.5 Flash):**
    *   Формування контекстно-залежного системного промпту з передачею поточного серверного часу та дня тижня (`now_str`) для обчислення відносних дат ("завтра", "у п'ятницю", "через 2 години").
    *   Structured JSON Output (`response_mime_type="application/json"`, низька `temperature=0.2`).
    *   Задачі моделі: класифікація категорії, генерація короткого заголовка, вибір типу розмітки (`TEXT` / `CHECKLIST` / `BULLET_LIST`), парсинг дедлайнів/нагадувань у формат `YYYY-MM-DD HH:MM`, генерація обґрунтування (`reasoning`).
*   **Offline / Fallback Heuristic Engine (`local_analyze`):**
    *   Спрацьовує автоматично при відсутності API-ключа або збоях зовнішнього AI API (мережевий таймаут, квота).
    *   Rule-based класифікація категорій за ключовими словами (`"Покупки"`, `"Навчання"`, `"Робота"`, `"Здоров'я"`, `"Особисте"`).
    *   Regex-виявлення часових тригерів ("завтра", "післязавтра", "о", "в") та розрахунок цільової дати від `datetime.now()`.
    *   Форматування: спліт за роздільниками (`,`, `;`, `\n`) у чекліст або збереження як текст.

---

## 4. Потік даних (Data Flow)

```text
[Користувач] 
     │ (вводить сирий текст)
     ▼
[MainScreen (UI)] 
     │ (перехід у UiState.Loading)
     ▼
[callBackendApi (Android IO Dispatcher)]
     │ HTTP POST /api/v1/notes/analyze {"text": "..."}
     ▼
[FastAPI Controller]
     │ валідація NoteAnalyzeRequest
     ├──► [Є GEMINI_API_KEY] ──► [Gemini 2.5 Flash] ──► [Structured JSON]
     │                                                │ (у разі збою / timeout)
     └──► [Немає ключа / Збій] ────────┴──────────────► [local_analyze (NLP Fallback)]
     │
     ▼
[FastAPI Response]
     │ HTTP 200 OK (NoteAnalyzeResponse)
     ▼
[callBackendApi]
     │ десеріалізація в NoteResponse
     ▼
[MainScreen (UI)]
     ▼
[UiState.Success] ──► Відображення структурованої картки користувачу
```

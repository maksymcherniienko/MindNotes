import os
import re
import json
from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from dotenv import load_dotenv
import google.generativeai as genai

load_dotenv()

app = FastAPI(
    title="MindNotes AI Backend",
    version="1.0.0",
    description="Backend API для обробки та структурування нотаток за допомогою AI"
)

# --- Pydantic моделі ---
class NoteAnalyzeRequest(BaseModel):
    text: str = Field(
        ...,
        min_length=3,
        max_length=2000,
        description="Неструктурований текст нотатки від користувача",
        examples=["В п'ятницю о 16:00 треба купити молоко та доробити лабораторну"]
    )

class NoteAnalyzeResponse(BaseModel):
    title: str
    category: str
    format_type: str  # TEXT, CHECKLIST, BULLET_LIST
    structured_content: str
    has_reminder: bool
    reminder_datetime: Optional[str] = None
    reasoning: str


# --- Fallback NLP функція (офлайн/без API ключа) ---
def local_analyze(text: str) -> NoteAnalyzeResponse:
    lower = text.lower()

    # Визначення категорії
    category = "Особисте"
    if any(w in lower for w in ["купити", "магазин", "молоко", "хліб", "ціна"]):
        category = "Покупки"
    elif any(w in lower for w in ["лабораторн", "пара", "універ", "викладач", "сесія", "іспит"]):
        category = "Навчання"
    elif any(w in lower for w in ["проєкт", "мітинг", "зустріч", "дедлайн", "робота"]):
        category = "Робота"
    elif any(w in lower for w in ["тренування", "зал", "лікар", "аптека"]):
        category = "Здоров'я"

    # Виявлення дати/часу
    # Динамічний розрахунок від поточного часу
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    has_reminder = False
    reminder_time = None
    if "завтра" in lower:
        target_date = now + timedelta(days=1)
        reminder_time = target_date.strftime("%Y-%m-%d 10:00")
        has_reminder = True
    elif "післязавтра" in lower:
        target_date = now + timedelta(days=2)
        reminder_time = target_date.strftime("%Y-%m-%d 10:00")
        has_reminder = True
    elif any(w in lower for w in ["дедлайн", "зустріч", "о ", "в "]):
        target_date = now + timedelta(hours=3)
        reminder_time = target_date.strftime("%Y-%m-%d %H:00")
        has_reminder = True

    # Форматування списку
    lines = [item.strip() for item in re.split(r"[,;\n]", text) if item.strip()]
    if len(lines) >= 2:
        format_type = "CHECKLIST"
        structured_content = "\n".join(f"- [ ] {l.capitalize()}" for l in lines)
    else:
        format_type = "TEXT"
        structured_content = text.strip()

    title = " ".join(text.split()[:4]).capitalize()

    return NoteAnalyzeResponse(
        title=title,
        category=category,
        format_type=format_type,
        structured_content=structured_content,
        has_reminder=has_reminder,
        reminder_datetime=reminder_time,
        reasoning="Оброблено локальним евристичним рушієм бекенду"
    )


# --- Ендпоінти ---
@app.get("/health", status_code=status.HTTP_200_OK)
def health_check():
    """Перевірка працездатності сервера"""
    return {"status": "ok", "service": "mindnotes-backend"}


@app.post("/api/v1/notes/analyze", response_model=NoteAnalyzeResponse, status_code=status.HTTP_200_OK)
def analyze_note(request: NoteAnalyzeRequest):
    """Основний ендпоінт для структурування нотатки через AI"""
    clean_text = request.text.strip()
    if not clean_text:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Текст нотатки не може бути порожнім"
        )

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        # Fallback якщо ключ не налаштовано
        return local_analyze(clean_text)

    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel(
            model_name="gemini-2.5-flash",
            generation_config={"response_mime_type": "application/json", "temperature": 0.2}
        )

        now_str = datetime.now().strftime("%Y-%m-%d %H:%M (%A)")

        prompt = f"""
        Ти — аналізатор нотаток.
        Поточний реальний час та дата на пристрої/сервері: {now_str}.
        Усі відносні дати ("завтра", "у п'ятницю", "післязавтра", "через 2 години") розраховуй виключно відносно цієї поточної дати!

        Оброби цей текст:
        "{clean_text}"

        Поверни JSON строго у такій структурі:
        {{
            "title": "Короткий заголовок (3-5 слів)",
            "category": "Навчання" | "Робота" | "Покупки" | "Особисте" | "Плани" | "Здоров'я",
            "format_type": "CHECKLIST" | "BULLET_LIST" | "TEXT",
            "structured_content": "відформатований текст списком або абзацами",
            "has_reminder": true чи false,
            "reminder_datetime": "YYYY-MM-DD HH:MM" або null,
            "reasoning": "коротке пояснення українською мовою"
        }}
        """

        response = model.generate_content(prompt)
        data = json.loads(response.text)
        return NoteAnalyzeResponse(**data)

    except Exception as e:
        # Якщо збій AI або мережі — повертаємо результат локального рушія
        fallback = local_analyze(clean_text)
        fallback.reasoning += f" (Збій AI: {str(e)[:50]})"
        return fallback
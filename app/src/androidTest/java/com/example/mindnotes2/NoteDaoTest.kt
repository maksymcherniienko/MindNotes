package com.example.mindnotes2

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mindnotes2.data.local.AppDatabase
import com.example.mindnotes2.data.local.NoteDao
import com.example.mindnotes2.data.local.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = database.noteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveNote() = runBlocking {
        val note = NoteEntity(
            title = "Тестова нотатка",
            category = "Покупки",
            formatType = "CHECKLIST",
            structuredContent = "- [ ] молоко",
            hasReminder = false,
            reminderDatetime = null,
            reasoning = "Тест"
        )

        val id = noteDao.insert(note)
        Assert.assertNotNull(id)

        val notes = noteDao.getAllNotes().first()
        Assert.assertEquals(1, notes.size)
        Assert.assertEquals("Тестова нотатка", notes[0].title)
    }

    @Test
    fun deleteNote_removesNoteFromDatabase() = runBlocking {
        // 1. Створюємо та додаємо нотатку
        val note = NoteEntity(
            title = "Нотатка для видалення",
            category = "Робота",
            formatType = "TEXT",
            structuredContent = "Текст задачі",
            hasReminder = false,
            reminderDatetime = null,
            reasoning = "Тест видалення"
        )
        val generatedId = noteDao.insert(note)

        // Отримуємо збережену нотатку з її згенерованим id
        val insertedNote = note.copy(id = generatedId)

        // 2. Видаляємо її
        noteDao.delete(insertedNote)

        // 3. Перевіряємо, що список тепер порожній
        val notes = noteDao.getAllNotes().first()
        Assert.assertEquals(0, notes.size)
    }

    @Test
    fun insertNote_withPriority_persistsCorrectly() = runBlocking {
// 1. Отримуємо розпізнані дані від AI/бекенду за сирим текстом
        val response = callBackendApi("Терміново доробити реліз")

        Assert.assertEquals("HIGH", response.priority)
    }
}


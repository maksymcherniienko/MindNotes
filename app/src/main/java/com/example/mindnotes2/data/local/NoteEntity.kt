package com.example.mindnotes2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val category: String,
    val formatType: String,
    val structuredContent: String,
    val hasReminder: Boolean,
    val reminderDatetime: String?,
    val reasoning: String,
    val createdAt: Long = System.currentTimeMillis()
)
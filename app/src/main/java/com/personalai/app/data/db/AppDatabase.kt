package com.personalai.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatMessageEntity::class, ChatSessionEntity::class, TaskEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "personal_ai.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

/**
 * Adds chat sessions and tasks without touching existing chat history. Pre-existing rows in
 * chat_messages (from before sessions existed) get folded into one "Previous chat" session so
 * a user's on-device history survives the upgrade instead of being wiped.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                lastMessagePreview TEXT NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                sourceSessionId INTEGER NOT NULL,
                isDone INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")

        val hasExistingMessages = db.query("SELECT COUNT(*) FROM chat_messages").use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }

        if (hasExistingMessages) {
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO chat_sessions (title, lastMessagePreview, createdAtMillis, updatedAtMillis) VALUES (?, ?, ?, ?)",
                arrayOf("Previous chat", "", now, now),
            )
            val defaultSessionId = db.query("SELECT last_insert_rowid()").use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }

            db.execSQL("UPDATE chat_messages SET sessionId = ? WHERE sessionId = 0", arrayOf(defaultSessionId))

            val preview = db.query(
                "SELECT content FROM chat_messages WHERE sessionId = ? ORDER BY timestampMillis DESC LIMIT 1",
                arrayOf(defaultSessionId),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" }

            db.execSQL(
                "UPDATE chat_sessions SET lastMessagePreview = ? WHERE id = ?",
                arrayOf(preview, defaultSessionId),
            )
        }
    }
}

/** Adds an optional reminder time to tasks so they can back a real OS alarm. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAtMillis INTEGER")
    }
}

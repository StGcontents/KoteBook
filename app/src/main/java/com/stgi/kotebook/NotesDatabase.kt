package com.stgi.kotebook

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note.NoteData::class], version = 6)
abstract class NotesDatabase : RoomDatabase() {
    companion object {
        private val migrationFrom5To6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE NoteData ADD COLUMN timestamp INTEGER")
            }
        }

        private var db: NotesDatabase? = null

        fun instance(context: Context): NotesDatabase {
            if (db == null) {
                db = Room
                    .databaseBuilder(
                        context,
                        NotesDatabase::class.java,
                        "notes-db")
                    .addMigrations(migrationFrom5To6)
                    .build()
            }
            return db!!
        }
    }
    abstract fun notesDao(): NoteDataDao
}
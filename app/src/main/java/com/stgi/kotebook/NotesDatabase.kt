package com.stgi.kotebook

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Note.NoteData::class], version = 6)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notesDao(): NoteDataDao
}
package com.stgi.kotebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


class NotesModel(application: Application) : AndroidViewModel(application) {

    private val db : NotesDatabase by lazy {
            NotesDatabase.instance(application)
    }

    val notes: LiveData<List<Note.NoteData>> by lazy {
        db.notesDao().getAll()
    }

    fun getAll(): List<Note.NoteData> = db.notesDao().getAllBlocking()

    fun update(note: Note.NoteData) {
        Thread(Runnable { db.notesDao().updateNotes(note) }).start()
    }

    fun remove(note: Note.NoteData) {
        Thread(Runnable { db.notesDao().delete(note) }).start()
    }

    fun add(note: Note.NoteData) {
        Thread(Runnable { db.notesDao().insertAll(note) }).start()
    }
}
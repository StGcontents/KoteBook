package com.stgi.kotebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.Room


class NotesModel(application: Application) : AndroidViewModel(application) {

    private val db : NotesDatabase by lazy {
            Room.databaseBuilder(
                application, NotesDatabase::class.java, "notes-db"
            ).fallbackToDestructiveMigration().build()
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
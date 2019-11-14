package com.stgi.kotebook

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteDataDao {
    @Query("SELECT * FROM notedata ORDER BY pinned DESC, uid")
    fun getAll(): LiveData<List<Note.NoteData>>

    @Query("SELECT * FROM notedata WHERE uid IN (:noteIds) ORDER BY pinned DESC, uid")
    fun loadAllByIds(noteIds: IntArray): LiveData<List<Note.NoteData>>

    @Query("SELECT * FROM notedata WHERE text LIKE :text LIMIT 1")
    fun findByText(text: String): Note.NoteData

    @Insert
    fun insertAll(vararg notes: Note.NoteData)

    @Update
    fun updateNotes(vararg notes: Note.NoteData)

    @Delete
    fun delete(note: Note.NoteData)

    @Query("DELETE FROM notedata WHERE uid = :id")
    fun delete(id: Int)
}

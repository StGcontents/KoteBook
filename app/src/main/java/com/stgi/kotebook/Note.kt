package com.stgi.kotebook

import android.graphics.Color
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import kotlin.math.pow

open class Note(data: NoteData) : Serializable {
    val id: Int = data.uid
    var title: String = data.title
    var text: String? = data.text
    var pinned: Boolean = data.pinned
    val isRecording: Boolean = data.isRecording
    var timestamp: Long? = data.timestamp
    val isTutorial: Boolean = data.isTutorial
    private var color: Int = data.color
    private var textColor: Int = calculateBest()

    fun setColor(color: Int) {
        this.color = color
        textColor = calculateBest()
    }

    fun getColor() = color
    fun getTextColor() = textColor

    private fun calculateBest() : Int {
        val distance = calculateDistance(color)
        val whiteDistance = calculateDistance(Color.WHITE)
        return if (distance < whiteDistance / 2) Color.WHITE else Color.BLACK
    }

    private fun calculateDistance(c : Int) =
        (c.red.toDouble().pow(2) + c.green.toDouble().pow(2) + c.blue.toDouble().pow(2)).pow(0.5)

    fun toData() = NoteData(title = title, text = text, color = color, isRecording = isRecording, timestamp = timestamp, pinned = pinned, isTutorial = isTutorial).also { it.uid = id }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) || (other is Note && other.id == this.id)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        return result
    }

    @Entity
    data class NoteData (
        @ColumnInfo(name = "text") var text: String?,
        @ColumnInfo(name = "pinned") var pinned: Boolean = false,
        @ColumnInfo(name = "title") var title: String = "",
        @ColumnInfo(name = "color") val color: Int,
        @ColumnInfo(name = "is_recording") val isRecording: Boolean = false,
        @ColumnInfo(name = "timestamp") var timestamp: Long? = null,
        @ColumnInfo(name = "is_tutorial") val isTutorial: Boolean = false
    ) {
        @PrimaryKey(autoGenerate = true) var uid: Int = 0
    }
}
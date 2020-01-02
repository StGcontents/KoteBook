package com.stgi.rodentia

import android.content.Context
import android.text.Editable
import android.util.Log
import android.view.View

fun Context.getDimen(resId: Int): Float = resources.getDimension(resId)
fun View.getDimen(resId: Int): Float = resources.getDimension(resId)
fun Context.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun View.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun Context.getNoDimen(): Int = getPixels(R.dimen.no_dimen)
fun View.getNoDimen(): Int = getPixels(R.dimen.no_dimen)

fun logd(tag: String? = "KoteBook", message: String?) {
    if (BuildConfig.DEBUG)
        Log.d(tag, message)
}

val palette = intArrayOf(
    R.color.noteColor31,
    R.color.noteColor28,
    R.color.noteColor34,
    R.color.noteColor30,

    R.color.noteColor35,
    R.color.noteColor25,
    R.color.noteColor26,
    R.color.noteColor17,
    R.color.noteColor21,

    R.color.noteColor33,
    R.color.noteColor12,
    R.color.noteColor10,
    R.color.noteColor9,
    R.color.noteColor11,
    R.color.noteColor13,

    R.color.noteColor16,
    R.color.noteColor15,
    R.color.noteColor14,
    R.color.noteColor19,
    R.color.noteColor18,
    R.color.noteColor22,
    R.color.noteColor24,

    R.color.noteColor0,
    R.color.noteColor1,
    R.color.noteColor2,
    R.color.noteColor3,
    R.color.noteColor5,
    R.color.noteColor36,
    R.color.noteColor8,
    R.color.noteColor7
)

fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
fun Editable.trimSpaces() = if (isEmpty()) this else trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() } as Editable
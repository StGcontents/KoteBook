package com.stgi.kotebook

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment

fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }

fun View.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun Fragment.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun Context.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
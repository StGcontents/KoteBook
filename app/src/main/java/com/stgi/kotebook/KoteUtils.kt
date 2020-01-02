package com.stgi.kotebook

import android.content.Context
import android.graphics.PorterDuff
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.View
import androidx.fragment.app.Fragment
import com.stgi.rodentia.BULLET_POINT_EMPTY
import com.stgi.rodentia.BULLET_POINT_FULL

const val ALPHA = 'α'
const val BETA = 'β'
const val GAMMA = 'γ'
const val DELTA = 'δ'
const val EPSILON = 'ε'
const val ZETA = 'ζ'
const val ETA = 'η'
const val THETA = 'θ'
const val IOTA = 'ι'

const val alphabet = "αβγδεζηθικ"

fun String.trimSpaces() =
    trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }

fun View.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun Fragment.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)
fun Context.getPixels(resId: Int): Int = resources.getDimensionPixelSize(resId)

fun String.indexOfFirstBullet(): Int =
    trimSpaces().indexOfFirst { c -> c == BULLET_POINT_FULL || c == BULLET_POINT_EMPTY }

fun String.trimBullets(): String {
    val str = trimSpaces()
    val index = str.indexOfFirstBullet()
    return when (index) {
        0 -> ""
        in 1..Int.MAX_VALUE -> str.substring(0, index)
        else -> str
    }.trimSpaces()
}

fun String.span(context: Context, color: Int): SpannableString {
    val spannable = SpannableString(trimSpaces())
    alphabet.forEach {
        if (spannable.contains(it)) {
            spannable.setSpan(
                getTutorialSpan(it, context, color),
                spannable.indexOf(it),
                spannable.indexOf(it) + 1,
                SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }
    }
    return spannable
}

private fun getTutorialSpan(char: Char, context: Context, color: Int) =
    ImageSpan(
            when (char) {
                ALPHA -> context.getDrawable(R.drawable.span_edit)
                BETA -> context.getDrawable(R.drawable.span_bullets)
                GAMMA -> context.getDrawable(R.drawable.enter)!!.mutate().apply { setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
                DELTA -> context.getDrawable(R.drawable.span_paint)
                EPSILON -> context.getDrawable(R.drawable.span_done)
                ZETA -> context.getDrawable(R.drawable.back)!!.mutate().apply { setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
                ETA -> context.getDrawable(R.drawable.span_add)
                THETA -> context.getDrawable(R.drawable.delete)!!.mutate().apply { setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
                IOTA -> context.getDrawable(R.drawable.span_pin)!!.mutate().apply { setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
                else -> context.getDrawable(R.drawable.span_edit)
            }!!.mutate().apply {
            val size = context.getPixels(R.dimen.span_size)
            setBounds(0, 0, size, size)
        },
        ImageSpan.ALIGN_BOTTOM
    )
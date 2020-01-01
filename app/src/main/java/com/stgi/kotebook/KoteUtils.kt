package com.stgi.kotebook

fun String.trimSpaces() = trimStart { c -> c.isWhitespace() }.dropLastWhile { c -> c.isWhitespace() }
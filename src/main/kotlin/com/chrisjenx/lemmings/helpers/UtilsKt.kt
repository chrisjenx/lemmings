package com.chrisjenx.lemmings.helpers

internal fun sanitizeFileName(candidate: String): String {
    return candidate.replace("[:\\\\/*\"?|<>']".toRegex(), "_")
}

internal fun generateDeviceName(prefix: String): String {
    val hex = getRandomString(10)
    return "$prefix-$hex"
}

internal fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length).map { allowedChars.random() }.joinToString("")
}

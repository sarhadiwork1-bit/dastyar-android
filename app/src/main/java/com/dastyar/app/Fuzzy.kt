package com.dastyar.app

object Fuzzy {
    /** Normalizes Persian/Arabic/Latin text for loose comparison. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (c in s.lowercase()) {
            when {
                c == 'ي' || c == 'ى' -> sb.append('ی')
                c == 'ك' -> sb.append('ک')
                c == 'ة' -> sb.append('ه')
                c == 'آ' || c == 'أ' || c == 'إ' -> sb.append('ا')
                c == '\u200c' -> sb.append(' ')
                c in '\u064B'..'\u065F' || c == '\u0640' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Lower is better; 99 = no match. Inputs must already be normalized. */
    fun score(name: String, q: String): Int {
        if (q.isEmpty()) return 99
        if (name == q) return 0
        if (name.contains(q)) return 1
        val tol = if (q.length <= 4) 1 else 2
        val best = name.split(' ').minOfOrNull { lev(it, q) } ?: 99
        return if (best <= tol) 2 else 99
    }

    fun asciiDigits(s: String): String =
        s.map { if (it.isDigit()) ('0' + Character.digit(it, 10)) else it }.joinToString("")

    /** True when the recognized text contains the wake word (or a configured variant). */
    fun wakeMatches(text: String): Boolean {
        val targets = (listOf(Prefs.wakeWord) + Prefs.wakeVariants.split(','))
            .map { norm(it).replace(" ", "") }
            .filter { it.length >= 2 }
        if (targets.isEmpty()) return false
        val tokens = norm(text).split(' ').filter { it.isNotEmpty() }
        for (i in tokens.indices) {
            var joined = ""
            // The wake phrase is expected to be one or two tokens; allowing three
            // makes ordinary Persian sentences more likely to trigger accidentally.
            for (j in i until minOf(i + 2, tokens.size)) {
                joined += tokens[j]
                for (t in targets) {
                    val tol = if (t.length <= 4) 0 else if (t.length <= 7) 1 else 2
                    if (lev(joined, t) <= tol) return true
                }
            }
        }
        return false
    }
}

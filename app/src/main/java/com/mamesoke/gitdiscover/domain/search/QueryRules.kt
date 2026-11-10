package com.mamesoke.gitdiscover.domain.search

import com.ibm.icu.text.BreakIterator
import java.util.Locale

class QueryRules(private val graphemeCount: (String) -> Int = ::countGraphemes) {
    fun normalized(text: String): String = text.trim()

    fun isValid(query: String): Boolean = graphemeCount(normalized(query)) >= 2
}

private fun countGraphemes(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

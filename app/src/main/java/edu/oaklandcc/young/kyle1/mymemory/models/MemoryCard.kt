package edu.oaklandcc.young.kyle1.mymemory.models

data class MemoryCard(
    val identifier: Int,
    val imagueUrl: String? = null,
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false

)

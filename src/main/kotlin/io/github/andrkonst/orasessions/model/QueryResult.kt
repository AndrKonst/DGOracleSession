package io.github.andrkonst.orasessions.model

data class QueryColumn(val name: String, val sqlType: Int)

data class QueryResult(
    val columns: List<QueryColumn>,
    val rows: List<List<Any?>>,
    val truncated: Boolean,
    val elapsedMs: Long,
)

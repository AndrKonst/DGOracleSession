package io.github.andrkonst.dgoraclesession.model

data class QueryColumn(val name: String, val sqlType: Int)

data class QueryResult(
    val columns: List<QueryColumn>,
    val rows: List<List<Any?>>,
    val truncated: Boolean,
    val elapsedMs: Long,
)

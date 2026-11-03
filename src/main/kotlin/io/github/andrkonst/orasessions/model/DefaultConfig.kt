package io.github.andrkonst.orasessions.model

object DefaultConfig {
    private val SESSIONS_SQL = """
        select sid, serial#, username, status, osuser, machine, program, module, sql_id, event, logon_time
          from v${'$'}session
         where type = 'USER'
         order by sid
    """.trimIndent()

    fun tabs(): MutableList<TabDef> = mutableListOf(TabDef(caption = "Sessions", sql = SESSIONS_SQL))
}

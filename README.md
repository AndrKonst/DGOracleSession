# Oracle Sessions for DataGrip

A DataGrip plugin that adds an **Oracle Sessions** tool window: configurable tabs with your own SQL queries
(sessions, locks, long operations — anything you like), detail sub-tabs for the selected row and
Kill / Disconnect session actions. Inspired by the Sessions window of PL/SQL Developer.

## Features

- **Query tabs.** Each tab runs its own SQL query against the selected Oracle data source and shows the
  result in the native DataGrip grid: sorting, search, copy, export and the standard auto-refresh control.
- **Details for the selected row.** A tab can have detail sub-tabs whose queries reference any column of the
  selected row as a bind variable: `:sid`, `:serial#`, `:sql_id`, …
  - *Table* mode shows the result in a grid.
  - *Text* mode shows SQL-highlighted text; rows are separated by new lines.
  - Details are refreshed when the selection changes and when the tab grid is reloaded.
- **HTML columns.** In *Table* details, cells of the columns listed in *HTML columns* show three icons instead of the text:
  open the value in a DataGrip editor tab (embedded browser), open it in the system browser, or copy it to the
  clipboard. Handy for
  `dbms_sqltune.report_sql_monitor` reports. If DataGrip loaded the value partially (LOB size limit), the detail
  query is re-executed and the row is found by the values of *Row key columns*; without them the value is not re-read.
- **Kill / Disconnect.** `ALTER SYSTEM KILL SESSION '…' IMMEDIATE` and
  `ALTER SYSTEM DISCONNECT SESSION '…' IMMEDIATE` for one or several selected rows, with confirmation.
  Available in tabs whose result has `SID` and `SERIAL#` columns.
- **Settings** in *Settings → Tools → Oracle Sessions*: add, remove and reorder tabs and details,
  edit their SQL with Oracle highlighting. Settings are global for the IDE.
- The selected row is kept across reloads by `SID` + `SERIAL#`.

## Requirements

- DataGrip 2026.1 or newer.
- An Oracle data source configured in DataGrip.
- Database privileges for the views used in your queries, e.g. `SELECT_CATALOG_ROLE`
  or `SELECT` on `V_$SESSION`, `V_$SQL`, `V_$LOCK`, …; `ALTER SYSTEM` for Kill / Disconnect.

## Installation

1. Build the plugin (see [Building from source](#building-from-source)).
2. In DataGrip: *Settings → Plugins → ⚙ → Install Plugin from Disk…* and choose the zip.
3. Open the **Oracle Sessions** tool window and select an Oracle data source.

## Configuration example

By default there is one *Sessions* tab:

```sql
select sid, serial#, username, status, osuser, machine, program, module, sql_id, event, logon_time
  from v$session
 where type = 'USER'
 order by sid
```

Detail examples for it:

| Caption  | Mode  | SQL |
|----------|-------|-----|
| SQL Text | Text  | `select sql_fulltext from v$sql where sql_id = :sql_id and rownum = 1` |
| Locks    | Table | `select * from v$lock where sid = :sid` |
| Wait     | Table | `select sid, event, wait_class, state, seconds_in_wait from v$session_wait where sid = :sid` |

SQL Monitor report with *HTML columns* `report` and *Row key columns* `sql_id, sql_exec_id`:

```sql
select sql_id, sql_exec_id, sql_exec_start, round(elapsed_time / 1e6, 2) elapsed_sec,
       dbms_sqltune.report_sql_monitor(sql_id => sql_id, sql_exec_id => sql_exec_id,
                                       sql_exec_start => sql_exec_start, type => 'ACTIVE') report
  from v$sql_monitor
 where sid = :sid and session_serial# = :serial#
 order by sql_exec_start desc
```

An `ACTIVE` report loads its scripts from `download.oracle.com`, so it needs internet access.

## Building from source

JDK 21 is required.

```bash
./gradlew buildPlugin      # build/distributions/oracle-sessions-<version>.zip
./gradlew test             # unit tests
./gradlew runIde           # sandbox DataGrip with the plugin
./gradlew verifyPlugin     # JetBrains Plugin Verifier
```

The build uses the local DataGrip installation from `platformLocalPath` in `gradle.properties`
(`/Applications/DataGrip.app` by default). If it does not exist, DataGrip `platformVersion` is downloaded.

## Notes

- The plugin relies on the DataGrip Database and Grid APIs, which are public but not documented and may change
  between major versions. Re-run `./gradlew verifyPlugin` after a DataGrip upgrade.
- Grids run in a dedicated DataGrip session named *Oracle Sessions* (visible in the Services tool window);
  text details and Kill / Disconnect use one more service connection. So the plugin has two sessions in `v$session`.

## License

[MIT](LICENSE)

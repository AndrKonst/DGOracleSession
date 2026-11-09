# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DataGrip plugin (Kotlin, IntelliJ Platform Gradle Plugin 2.x) that adds the **DGOracleSession** tool window: configurable tabs with user SQL queries against an Oracle data source, detail sub-tabs for the selected row, and Kill / Disconnect session actions. User-facing behaviour is documented in `README.md` — keep it in sync when behaviour changes.

## Commands

JDK 21 is required.

```bash
./gradlew buildPlugin      # build/distributions/DGOracleSession-<version>.zip
./gradlew test             # unit tests (JUnit 4)
./gradlew test --tests "io.github.andrkonst.dgoraclesession.db.BindParserTest"   # single test class
./gradlew runIde           # sandbox DataGrip with the plugin
./gradlew verifyPlugin     # JetBrains Plugin Verifier
```

- The platform comes from `platformLocalPath` in `gradle.properties` (`/Applications/DataGrip.app`); if that path does not exist, DataGrip `platformVersion` is downloaded. Same logic for `verifyPlugin`.
- `pluginSinceBuild = 261`, no until-build.
- Kotlin is compiled with `jvmDefault = NO_COMPATIBILITY` on purpose: without it Plugin Verifier reports deprecated/experimental `ToolWindowFactory` defaults as overridden. Do not remove it.
- The DataGrip Database/Grid APIs (`com.intellij.database.*`) are public but undocumented and change between major versions — run `verifyPlugin` after touching them or after a DataGrip upgrade.

## Plugin version

- Every change to the plugin must come with a version bump in `gradle.properties` (`version = X.Y.Z`).
- Bump patch by default (`0.3.0` → `0.3.1`); minor/major only when the user asks for it.

## Git

- Do not credit Claude as a co-author: no `Co-Authored-By: Claude …` in commits and no `Generated with Claude Code` in PRs.

## Architecture

Package root: `src/main/kotlin/io/github/andrkonst/dgoraclesession/`. Extensions and actions are registered in `src/main/resources/META-INF/plugin.xml`; all UI strings live in `messages/DGOracleSessionBundle.properties` (accessed via `DGOracleSessionBundle.message`).

**Two ways of executing SQL — this split drives most of the design:**

1. **DataGrip grid** (`grid/QueryGrid`): a read-only `DatabaseGridDataHookUp` running in a dedicated DataGrip session titled `DGOracleSession`. Gives the native grid (sort, export, auto-refresh), but accepts only plain SQL text. Used for tab queries and *Table*-mode details — so `DetailGridPanel` inlines bind values as literals via `BindParser.inline`.
2. **Service JDBC connections** (`SessionsService`, project-level): two `ConnectionSlot`s, each with a mutex-serialized `OracleConnectionHolder` (built via `DatabaseConnectionManager`, auto-reconnect on connection-lost ORA/JDBC codes). `query()` slot is for *Text* details and re-reading truncated HTML values (real positional binds via `BindParser.toPositional`); `execute()`/`adminInstance()` slot is for Kill / Disconnect, separate so termination never waits on a slow query. Coroutine cancellation is propagated to JDBC through `StatementCanceller`. `QueryExecutor` does the raw JDBC over `RemoteConnection` (strips trailing `;`, max rows, timeout).

So the plugin holds up to three Oracle sessions. Changing the data source (`SessionsService.selectDataSource`, persisted in `PropertiesComponent`) closes both service connections.

**UI hierarchy:**
- `SessionsToolWindowFactory` → `SessionsPanel`: data-source combo (Oracle sources only, coloured by `DatabaseColorManager`), toolbar, one `QueryTabPanel` per configured tab. Rebuilds tabs on `QueryTabsSettings.TOPIC`.
- `QueryTabPanel`: grid created lazily on first activation; keeps the selected row across reloads by a key from `GridRows` (SID + SERIAL#); debounces selection (300 ms `Alarm`) before updating details. Exposes itself via `QueryTabPanel.KEY` data key — that is how `TerminateSessionAction` finds selected sessions (`SessionRef.fromRow`, requires `SID`/`SERIAL#`, optional `INST_ID` for RAC kill).
- `detail/DetailPanel` (abstract): only the active sub-tab executes; inactive ones are marked stale. Missing bind columns are reported instead of executing. Subclasses: `DetailGridPanel` (grid + `HtmlColumns` icon renderer) and `DetailTextPanel` (service connection, SQL-highlighted text).
- HTML columns: `grid/HtmlColumns` paints open/browser/copy icons in cells; `html/HtmlReportOpener` + `HtmlReportEditor` (a `FileEditorProvider`) show the report in a JCEF editor tab. Truncated CLOBs are re-read through `model/RowKeys.filter` — wraps the detail query as `select * from (…) where "KEY" = ?` using *Row key columns*.

**Settings:** `QueryTabsSettings` is an application-level `PersistentStateComponent` (`dgOracleSession.xml`) storing `TabDef`/`DetailDef` (xmlb-annotated data classes; getters/setters return deep copies). Defaults in `model/DefaultConfig`. Editing UI: `QueryTabsConfigurable` under *Settings → Tools*. `QueryTabsSettingsSerializationTest` guards the XML format — keep attributes backward compatible.

Unit tests cover only pure logic (`BindParser`, `RowKeys`, `SessionRef`, settings serialization); UI and DB code is verified manually via `runIde`.

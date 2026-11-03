package io.github.andrkonst.orasessions.grid

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.remote.jdbc.LobInfo
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.DataAccessType

/** Values of grid rows keyed by lower-cased column name. */
object GridRows {
    private val KEY_COLUMNS = listOf("sid", "serial#")

    fun selectedValues(grid: DataGrid): Map<String, Any?>? {
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        val row = grid.selectionModel.leadSelectionRow
        if (!row.isValid(model)) return null
        return rowValues(model, row)
    }

    fun selectedRowsValues(grid: DataGrid): List<Map<String, Any?>> {
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        return grid.selectionModel.selectedRows.asList()
            .filter { it.isValid(model) }
            .map { rowValues(model, it) }
    }

    /** Row identity for keeping the selection across reloads: SID + SERIAL# when both columns exist. */
    fun key(values: Map<String, Any?>): List<String>? =
        if (KEY_COLUMNS.all { it in values }) KEY_COLUMNS.map { values[it]?.toString().orEmpty() } else null

    fun findRow(grid: DataGrid, key: List<String>): ModelIndex<GridRow>? {
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        for (i in 0 until model.rowCount) {
            val row = ModelIndex.forRow(model, i)
            if (key(rowValues(model, row)) == key) return row
        }
        return null
    }

    fun rowValues(grid: DataGrid, row: ModelIndex<GridRow>): Map<String, Any?> =
        rowValues(grid.getDataModel(DataAccessType.DATABASE_DATA), row)

    private fun rowValues(model: GridModel<GridRow, GridColumn>, row: ModelIndex<GridRow>): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>()
        model.columns.forEachIndexed { i, column ->
            values[column.name.lowercase()] = normalize(model.getValueAt(row, ModelIndex.forColumn(model, i)))
        }
        return values
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is ReservedCellValue -> null
        is LobInfo.ClobInfo -> value.data
        else -> value
    }
}

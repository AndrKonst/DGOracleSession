package io.github.andrkonst.dgoraclesession.grid

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.grid.renderers.DefaultBooleanRendererFactory
import com.intellij.database.run.ui.grid.renderers.DefaultNumericRendererFactory
import com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer
import com.intellij.database.run.ui.grid.renderers.GridCellRendererFactories
import com.intellij.database.run.ui.grid.renderers.GridCellRendererFactory
import com.intellij.util.ui.JBUI
import io.github.andrkonst.dgoraclesession.html.HtmlReportOpener
import io.github.andrkonst.dgoraclesession.html.HtmlTarget
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable

/**
 * Replaces the text of non-null cells of the given columns with "open in DataGrip", "open in browser" and "copy"
 * icons and handles the clicked one. The icons are painted by a cell renderer, clicks are hit-tested against the same geometry.
 */
object HtmlColumns {
    fun interface OpenHandler {
        fun open(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, target: HtmlTarget)
    }

    fun install(grid: DataGrid, columns: Set<String>, handler: OpenHandler) {
        if (columns.isEmpty()) return
        val targets = HtmlReportOpener.targets()
        val text = DefaultTextRendererFactory(grid)
        // Same factories as DataGridUtil.configure sets for database grids, plus the HTML one
        GridCellRendererFactories.set(
            grid,
            GridCellRendererFactories(
                listOf(
                    DefaultBooleanRendererFactory(grid),
                    DefaultNumericRendererFactory(grid),
                    text,
                    HtmlRendererFactory(grid, columns, text, targets),
                ),
            ),
        )
        val table = grid.resultView.component as? JTable ?: return
        val mouse = IconMouseHandler(grid, table, columns, targets, handler)
        table.addMouseListener(mouse)
        table.addMouseMotionListener(mouse)
    }

    private fun isHtmlColumn(grid: DataGrid, columns: Set<String>, column: ModelIndex<GridColumn>): Boolean {
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        if (!column.isValid(model)) return false
        return model.getColumn(column)?.name?.lowercase() in columns
    }

    private fun hasValue(value: Any?) = value != null && value !is ReservedCellValue

    private class HtmlRendererFactory(
        private val grid: DataGrid,
        private val columns: Set<String>,
        private val text: GridCellRendererFactory,
        private val targets: List<HtmlTarget>,
    ) : GridCellRendererFactory {
        private var renderer: HtmlCellRenderer? = null

        override fun supports(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>) = isHtmlColumn(grid, columns, column)

        override fun getOrCreateRenderer(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): GridCellRenderer =
            renderer ?: HtmlCellRenderer(grid, columns, text, targets).also { renderer = it }
    }

    private class HtmlCellRenderer(
        grid: DataGrid,
        private val columns: Set<String>,
        private val text: GridCellRendererFactory,
        targets: List<HtmlTarget>,
    ) : GridCellRenderer(grid) {
        private val panel = JPanel(BorderLayout()).apply { add(IconStrip(targets), BorderLayout.WEST) }

        override fun getSuitability(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>) =
            if (isHtmlColumn(myGrid, columns, column)) SUITABILITY_MAX else SUITABILITY_UNSUITABLE

        override fun getComponent(row: ViewIndex<GridRow>, column: ViewIndex<GridColumn>, value: Any?): JComponent {
            val delegate = text.getOrCreateRenderer(row.toModel(myGrid), column.toModel(myGrid)).getComponent(row, column, value)
            if (!hasValue(value)) return delegate
            // The text renderer is still asked for the component to keep its selection and stripe background
            panel.background = delegate.background
            panel.isOpaque = true
            return panel
        }

        override fun reinitSettings() = Unit
    }

    private class IconStrip(private val targets: List<HtmlTarget>) : JComponent() {
        override fun getPreferredSize() = Dimension(width(targets), targets.maxOf { it.icon.iconHeight })

        override fun paintComponent(g: Graphics) {
            val step = step(targets)
            targets.forEachIndexed { i, target ->
                target.icon.paintIcon(this, g, JBUI.scale(PAD) + i * step, (height - target.icon.iconHeight) / 2)
            }
        }

        companion object {
            private const val PAD = 4
            private const val GAP = 4

            private fun iconWidth(targets: List<HtmlTarget>) = targets.maxOf { it.icon.iconWidth }

            private fun step(targets: List<HtmlTarget>) = iconWidth(targets) + JBUI.scale(GAP)

            fun width(targets: List<HtmlTarget>) = 2 * JBUI.scale(PAD) + targets.size * step(targets) - JBUI.scale(GAP)

            /** Target whose icon is under [point] in a cell painted at [cell]. */
            fun hit(targets: List<HtmlTarget>, cell: Rectangle, point: Point): HtmlTarget? {
                val x = point.x - cell.x - JBUI.scale(PAD)
                if (x < 0) return null
                val index = x / step(targets)
                if (index >= targets.size || x % step(targets) >= iconWidth(targets)) return null
                return targets[index]
            }
        }
    }

    private class IconMouseHandler(
        private val grid: DataGrid,
        private val table: JTable,
        private val columns: Set<String>,
        private val targets: List<HtmlTarget>,
        private val handler: OpenHandler,
    ) : MouseAdapter() {
        private var handCursor = false

        private data class Hit(val row: ModelIndex<GridRow>, val column: ModelIndex<GridColumn>, val target: HtmlTarget)

        private fun hit(e: MouseEvent): Hit? {
            if (grid.resultView.isTransposed) return null
            val viewRow = table.rowAtPoint(e.point)
            val viewColumn = table.columnAtPoint(e.point)
            if (viewRow < 0 || viewColumn < 0) return null
            val row = ViewIndex.forRow(grid, viewRow).toModel(grid)
            val column = ViewIndex.forColumn(grid, viewColumn).toModel(grid)
            val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
            if (!row.isValid(model) || !isHtmlColumn(grid, columns, column)) return null
            if (!hasValue(model.getValueAt(row, column))) return null
            val target = IconStrip.hit(targets, table.getCellRect(viewRow, viewColumn, false), e.point) ?: return null
            return Hit(row, column, target)
        }

        override fun mouseClicked(e: MouseEvent) {
            if (e.button != MouseEvent.BUTTON1 || e.clickCount != 1) return
            val hit = hit(e) ?: return
            e.consume()
            handler.open(hit.row, hit.column, hit.target)
        }

        override fun mouseMoved(e: MouseEvent) = setHandCursor(hit(e) != null)

        override fun mouseExited(e: MouseEvent) = setHandCursor(false)

        private fun setHandCursor(value: Boolean) {
            if (value == handCursor) return
            handCursor = value
            table.cursor = if (value) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        }
    }
}

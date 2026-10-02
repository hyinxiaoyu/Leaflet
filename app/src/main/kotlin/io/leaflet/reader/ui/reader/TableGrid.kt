package io.leaflet.reader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/** Largest single dimension Compose's constraints can represent, with a margin.
 *
 * The value is packed into 18 bits and the pinned `ui-unit` 1.7.5 factory throws
 * `IllegalArgumentException: Can't represent a width of 262143 … in Constraints`
 * from 262143 upward (verified with a JVM probe: 262142 is accepted, 262143
 * throws). Cell content is never assumed to fit: [TableBlockEntry] validates the
 * block against this bound and uses its scrollable text fallback instead
 * (CR-20261001-01), while this clamp keeps an unrepresentable intrinsic width
 * from crashing layout even if a future caller forgets to validate.
 */
internal const val MAX_CONSTRAINT_DIMENSION_PX = 260_000

/**
 * Upper bound, in pixels, on the width of ONE unwrapped table-cell line
 * (CR-20261001-01).
 *
 * Cells render with `softWrap = false`, so the grid asks for each cell's full
 * single-line width and [MAX_CONSTRAINT_DIMENSION_PX] is the hard ceiling.
 * Every glyph advance in the body/code fonts is bounded by ~2 em (CJK ≈ 1 em,
 * wide emoji and fallback glyphs ≈ 1.6 em, while bold and the default letter
 * spacing add far less than an extra em), so `characters × 2 em + padding` never
 * underestimates the measured line and is safe as a pre-measurement gate.
 */
internal fun unwrappedCellWidthUpperBoundPx(
    characters: Int,
    emPx: Float,
    paddingPx: Float,
): Float = characters * 2f * emPx + paddingPx

/**
 * Whether a table cell of [characters] characters can be measured as fixed
 * constraints. When this is false the caller must use its bounded, scrollable
 * text fallback instead of the grid.
 */
internal fun isTableCellWidthRepresentable(
    characters: Int,
    emPx: Float,
    paddingPx: Float,
): Boolean = unwrappedCellWidthUpperBoundPx(characters, emPx, paddingPx) <= MAX_CONSTRAINT_DIMENSION_PX

/** Shared column widths and row heights, measured from the actual styled cell content. */
@Composable
internal fun TableGrid(
    rowCount: Int,
    columnCount: Int,
    cellContent: @Composable (row: Int, column: Int) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    Layout(
        content = {
            repeat(rowCount) { row ->
                repeat(columnCount) { column ->
                    Box(
                        Modifier.drawBehind {
                            val stroke = 1.dp.toPx()
                            if (row == 0) {
                                drawLine(borderColor, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                            }
                            if (column == 0) {
                                drawLine(borderColor, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                            }
                            drawLine(
                                borderColor,
                                Offset(size.width - stroke / 2, 0f),
                                Offset(size.width - stroke / 2, size.height),
                                stroke,
                            )
                            val bottomStroke = if (row == 0) 2.dp.toPx() else stroke
                            drawLine(
                                borderColor,
                                Offset(0f, size.height - bottomStroke / 2),
                                Offset(size.width, size.height - bottomStroke / 2),
                                bottomStroke,
                            )
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) { cellContent(row, column) }
                }
            }
        },
    ) { measurables, constraints ->
        // Intrinsics include padding and inline styles, including bold headers and code fonts.
        // Measuring each cell only once avoids state-driven layout feedback and row-by-row drift.
        // Both dimensions are clamped to what Compose can represent: an unrepresentable
        // value would throw here instead of rendering anything (CR-20261001-01).
        val minColumnPx = 48.dp.roundToPx()
        val widths =
            IntArray(columnCount) { column ->
                (0 until rowCount).maxOf { row ->
                    measurables[row * columnCount + column].maxIntrinsicWidth(Constraints.Infinity)
                }.coerceIn(minColumnPx, MAX_CONSTRAINT_DIMENSION_PX)
            }
        val heights =
            IntArray(rowCount) { row ->
                (0 until columnCount).maxOf { column ->
                    measurables[row * columnCount + column].maxIntrinsicHeight(widths[column])
                }.coerceAtMost(MAX_CONSTRAINT_DIMENSION_PX)
            }
        val cells =
            measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixed(widths[index % columnCount], heights[index / columnCount]))
            }
        layout(constraints.constrainWidth(widths.sum()), constraints.constrainHeight(heights.sum())) {
            var y = 0
            repeat(rowCount) { row ->
                var x = 0
                repeat(columnCount) { column ->
                    cells[row * columnCount + column].placeRelative(x, y)
                    x += widths[column]
                }
                y += heights[row]
            }
        }
    }
}

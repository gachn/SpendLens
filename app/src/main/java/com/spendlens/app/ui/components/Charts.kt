package com.spendlens.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    diameter: Dp = 160.dp,
    strokeWidth: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val total = values.sum().takeIf { it > 0f } ?: 1f
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            val topLeft = Offset(inset, inset)
            if (values.isEmpty()) {
                drawArc(Color(0x22FFFFFF), 0f, 360f, false, topLeft, arcSize, style = stroke)
            }
            var startAngle = -90f
            values.forEachIndexed { i, v ->
                val sweep = v / total * 360f
                drawArc(
                    color = colors.getOrElse(i) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        center()
    }
}

/**
 * Grouped bar chart: two series per label (e.g. spent / received).
 * Tap a bar group to toggle a tooltip showing the exact values for that slot.
 */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    series1: List<Float>,
    series2: List<Float>,
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    series1Label: String = "",
    series2Label: String = "",
    formatValue: (Float) -> String = { it.toLong().toString() },
) {
    val max = (series1 + series2).maxOrNull()?.takeIf { it > 0f } ?: 1f
    var selected by remember(labels) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val tooltipText = MaterialTheme.colorScheme.onSurface
    val tooltipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val tooltipBorder = Color.White.copy(alpha = 0.12f)

    Canvas(
        modifier = modifier
            .height(height)
            .pointerInput(labels.size) {
                detectTapGestures { offset ->
                    val n = labels.size.coerceAtLeast(1)
                    val slot = size.width / n
                    val idx = (offset.x / slot).toInt().coerceIn(0, n - 1)
                    selected = if (selected == idx) null else idx
                }
            },
    ) {
        val n = labels.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = slot / 4f
        val gap = barW * 0.4f
        val baseline = size.height
        for (i in labels.indices) {
            val cx = slot * i + slot / 2f
            val h1 = (series1.getOrElse(i) { 0f } / max) * size.height
            val h2 = (series2.getOrElse(i) { 0f } / max) * size.height
            val dim = selected != null && selected != i
            drawRoundBar(cx - barW - gap / 2f, baseline, barW, h1, if (dim) color1.copy(alpha = 0.3f) else color1)
            drawRoundBar(cx + gap / 2f, baseline, barW, h2, if (dim) color2.copy(alpha = 0.3f) else color2)
        }

        // Tooltip for the selected slot
        selected?.let { idx ->
            val text = buildString {
                append(labels.getOrElse(idx) { "" })
                if (series1Label.isNotEmpty()) append("\n$series1Label: ${formatValue(series1.getOrElse(idx) { 0f })}")
                if (series2Label.isNotEmpty()) append("\n$series2Label: ${formatValue(series2.getOrElse(idx) { 0f })}")
            }
            val layout = textMeasurer.measure(text, style = TextStyle(fontSize = 11.sp, color = tooltipText))
            val pad = 8.dp.toPx()
            val boxW = layout.size.width + pad * 2
            val boxH = layout.size.height + pad * 2
            val cx = slot * idx + slot / 2f
            val left = (cx - boxW / 2f).coerceIn(0f, (size.width - boxW).coerceAtLeast(0f))
            val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            drawRoundRect(tooltipBg, Offset(left, 0f), Size(boxW, boxH), radius)
            drawRoundRect(tooltipBorder, Offset(left, 0f), Size(boxW, boxH), radius, style = Stroke(width = 1.dp.toPx()))
            drawText(layout, topLeft = Offset(left + pad, pad))
        }
    }
}

/** Circular progress ring drawn on Canvas. Used by the Budgets screen. */
@Composable
fun CircularProgressRing(
    progress: Float,            // 0f..1f
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    strokeWidth: Dp = 6.dp,
    center: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            val topLeft = Offset(inset, inset)
            // Track
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = stroke)
            // Progress
            val sweep = (progress * 360f).coerceIn(0f, 360f)
            if (sweep > 0f) {
                drawArc(progressColor, -90f, sweep, false, topLeft, arcSize, style = stroke)
            }
        }
        center()
    }
}

private fun DrawScope.drawRoundBar(x: Float, baseline: Float, width: Float, height: Float, color: Color) {
    if (height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, baseline - height),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, width / 2f),
    )
}

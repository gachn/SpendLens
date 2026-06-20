package com.spendlens.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight charts drawn on Compose Canvas — no third-party chart dependency
 * (keeps the trusted surface small, docs/DESIGN.md §8.3).
 */

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
                drawArc(Color(0x22000000), 0f, 360f, false, topLeft, arcSize, style = stroke)
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

/** Grouped bar chart: two series (e.g. debit/credit) per label. */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    series1: List<Float>,
    series2: List<Float>,
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val max = (series1 + series2).maxOrNull()?.takeIf { it > 0f } ?: 1f
    Canvas(modifier = modifier.height(height)) {
        val n = labels.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = slot / 4f
        val gap = barW * 0.4f
        val baseline = size.height
        for (i in 0 until labels.size) {
            val cx = slot * i + slot / 2f
            val h1 = (series1.getOrElse(i) { 0f } / max) * size.height
            val h2 = (series2.getOrElse(i) { 0f } / max) * size.height
            drawRoundBar(cx - barW - gap / 2f, baseline, barW, h1, color1)
            drawRoundBar(cx + gap / 2f, baseline, barW, h2, color2)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundBar(
    x: Float,
    baseline: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    if (height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, baseline - height),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f, width / 2f),
    )
}

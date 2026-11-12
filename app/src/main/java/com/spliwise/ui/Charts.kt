package com.spliwise.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable
private fun rememberGrow(key: Any?): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    return a.value
}

/** Animated donut with rounded segments. */
@Composable
fun Donut(slices: List<Pair<Color, Long>>, modifier: Modifier = Modifier, size: Dp = 170.dp, stroke: Dp = 22.dp, center: @Composable () -> Unit = {}) {
    val grow = rememberGrow(slices)
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(P.Line, 0f, 360f, false, tl, arc, style = Stroke(sw))
            var start = -90f
            val gap = if (slices.size > 1) 4f else 0f
            slices.forEach { (c, v) ->
                val sweep = 360f * v / total * grow
                if (sweep > gap) drawArc(c, start + gap / 2, sweep - gap, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                start += sweep
            }
        }
        center()
    }
}

/** Vertical bars with labels; tap a bar to select it. */
@Composable
fun BarChart(
    values: List<Long>, labels: List<String>, selected: Int?, modifier: Modifier = Modifier,
    height: Dp = 150.dp, labelEvery: Int = 1, onSelect: (Int) -> Unit = {},
) {
    val grow = rememberGrow(values)
    val tm = rememberTextMeasurer()
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(values.size) {
            detectTapGestures { o -> onSelect((o.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)) }
        }
    ) {
        if (values.isEmpty()) return@Canvas
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / values.size
        val w = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (chartH * v / max * grow).coerceAtLeast(if (v > 0) 4f else 2f)
            val x = i * slot + (slot - w) / 2
            val sel = selected == i
            drawRoundRect(
                brush = if (v == 0L) Brush.linearGradient(listOf(P.Line, P.Line))
                else if (sel) Brush.verticalGradient(listOf(P.Orange, P.Pink)) else P.Bars,
                topLeft = Offset(x, chartH - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
                alpha = if (selected == null || sel || v == 0L) 1f else .55f,
            )
            if (i % labelEvery == 0 || sel) {
                val r = tm.measure(labels[i], TextStyle(fontSize = 10.sp, color = if (sel) P.Ink else P.Sub))
                drawText(r, topLeft = Offset(i * slot + (slot - r.size.width) / 2, chartH + 4.dp.toPx()))
            }
        }
    }
}

/** Month calendar where colour intensity = how much was spent that day. */
@Composable
fun Heatmap(ym: YearMonth, perDay: LongArray, selected: Int?, onSelect: (Int) -> Unit) {
    val max = (perDay.maxOrNull() ?: 0L).coerceAtLeast(1)
    val offset = ym.atDay(1).dayOfWeek.value - 1 // Monday first
    val cells = offset + perDay.size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, color = P.Sub, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 0 until 7) {
                    val day = week * 7 + d - offset
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in perDay.indices) {
                            val f = perDay[day].toFloat() / max
                            val sel = selected == day
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (sel) Brush.linearGradient(listOf(P.Orange, P.Pink))
                                        else if (perDay[day] == 0L) Brush.linearGradient(listOf(P.Line, P.Line))
                                        else Brush.linearGradient(listOf(P.Violet.copy(alpha = .18f + .82f * f), P.Pink.copy(alpha = .18f + .82f * f)))
                                    )
                                    .clickable { onSelect(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${day + 1}", fontSize = 11.sp, color = if (sel || f > .5f) Color.White else P.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Horizontal share bars for the category breakdown. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val grow = rememberGrow(fraction)
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f))) {
        Box(
            Modifier.fillMaxWidth((fraction * grow).coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = .7f), color)))
        )
    }
}

@Composable
fun Legend(color: Color, text: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text("  $text", color = P.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, color = P.Sub, fontSize = 13.sp)
    }
}

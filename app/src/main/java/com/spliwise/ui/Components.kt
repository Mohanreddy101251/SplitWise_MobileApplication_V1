package com.spliwise.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.core.Money
import com.spliwise.data.Category
import com.spliwise.data.Source
import com.spliwise.data.Txn
import java.time.format.DateTimeFormatter
import com.spliwise.core.Dates

fun Category.c() = Color(color)
private val fallback = Category("unknown", "Needs review", "❓", 0xFFB8B5C9)
fun Map<String, Category>.of(id: String) = this[id] ?: fallback

@Composable
fun SCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = P.Violet.copy(alpha = .12f), spotColor = P.Violet.copy(alpha = .18f))
            .clip(shape)
            .background(P.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(pad),
        content = content,
    )
}

/** Gradient header that runs under the status bar. */
@Composable
fun Header(brush: Brush = P.Hero, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(brush)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        content = content,
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = P.Ink, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = P.Violet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAction?.invoke() }.padding(6.dp))
    }
}

@Composable
fun CatBubble(cat: Category, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.36f))
            .background(Brush.linearGradient(listOf(cat.c().copy(alpha = .22f), cat.c().copy(alpha = .38f)))),
        contentAlignment = Alignment.Center,
    ) { Text(cat.emoji, fontSize = (size.value * 0.48f).sp) }
}

@Composable
fun Avatar(name: String, size: Dp = 42.dp) {
    val colors = listOf(P.Violet, P.Pink, P.Teal, P.Orange, P.Sky, P.Magenta)
    val c = colors[(name.hashCode() and 0x7fffffff) % colors.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c, c.copy(alpha = .65f)))),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * .42f).sp) }
}

@Composable
fun Pill(text: String, bg: Color = Color.White.copy(alpha = .22f), fg: Color = Color.White, modifier: Modifier = Modifier) {
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = P.Violet, onClick: () -> Unit) {
    Text(
        text, maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else P.Ink,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = .10f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Counts up to the value; final frame is always the exact amount. */
@Composable
fun AnimatedMoney(paise: Long, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(paise) { p.snapTo(0f); p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val v = if (p.value >= 1f) paise else (paise * p.value.toDouble()).toLong()
    Text(Money.format(v, decimals = false), style = style, color = color, modifier = modifier)
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

/** "HDFC Bank a/c ••4321" or "HDFC Bank credit card ••1234". */
fun accountLabel(t: Txn): String {
    val bank = com.spliwise.ingest.Banks.byId(t.bank)?.name ?: t.bank?.uppercase() ?: "Bank"
    return "$bank ${if (t.card) "credit card" else "a/c"}" + (t.account?.let { " ••$it" } ?: "")
}

@Composable
fun TxnRow(t: Txn, cats: Map<String, Category>, onClick: () -> Unit) {
    val cat = cats.of(t.category)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble(cat)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant.ifBlank { t.note.ifBlank { cat.name } }, fontWeight = FontWeight.SemiBold, color = P.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dt = java.time.Instant.ofEpochMilli(t.ts).atZone(Dates.zone)
            val src = when (t.source) { Source.VOICE -> " · 🎙"; Source.SMS -> " · ✉"; Source.PDF -> " · 📄"; Source.MINI -> " · ✉"; else -> "" }
            Text(
                "${cat.name} · ${if (t.source == Source.PDF || t.source == Source.MINI) dt.format(dayFmt) else dt.format(timeFmt)}$src",
                color = if (t.needsReview) P.Orange else P.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Which bank / account / card the money left or reached
            if (t.bank != null) Text(
                (if (t.isCredit) "Credited to " else "Debited from ") + accountLabel(t),
                color = P.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (t.isCredit) "+" else "−") + Money.format(t.amount),
            fontWeight = FontWeight.Bold, color = when {
                t.category in com.spliwise.classify.Categories.TRANSFERS -> P.Sub
                t.isCredit -> P.Green
                else -> P.Ink
            },
        )
    }
}

@Composable
fun BudgetBar(spent: Long, budget: Long, color: Color, modifier: Modifier = Modifier) {
    val f = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { f }, modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = if (budget > 0 && spent > budget) P.Red else color, trackColor = color.copy(alpha = .15f),
        drawStopIndicator = {},
    )
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, light: Boolean = true) {
    Column(modifier) {
        Text(label, color = if (light) Color.White.copy(alpha = .8f) else P.Sub, fontSize = 12.sp)
        Text(value, color = if (light) Color.White else P.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, brush: Brush = P.Hero, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(P.Line, P.Line)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Color.White else P.Sub, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun RowScope.Gap(w: Dp = 8.dp) = Spacer(Modifier.width(w))

@Composable
fun Empty(emoji: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 42.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, color = P.Sub, fontSize = 14.sp)
    }
}

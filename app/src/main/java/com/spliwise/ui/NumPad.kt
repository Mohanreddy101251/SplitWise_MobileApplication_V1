package com.spliwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumPad(decimal: Boolean = true, modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (decimal) "." else "", "0", "⌫"))
    Column(modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (k.isEmpty()) P.Card else P.Violet.copy(alpha = .07f))
                            .clickable(enabled = k.isNotEmpty()) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = P.Ink) }
                }
            }
        }
    }
}

/** Applies a numpad key to an amount string ("123.45"), max 2 decimals, max ₹1 crore. */
fun applyKey(cur: String, k: String): String = when {
    k == "⌫" -> cur.dropLast(1)
    k == "." -> if ('.' in cur) cur else (cur.ifEmpty { "0" } + ".")
    '.' in cur && cur.substringAfter('.').length >= 2 -> cur
    cur == "0" -> k
    cur.substringBefore('.').length >= 8 && '.' !in cur -> cur
    else -> cur + k
}

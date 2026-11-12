package com.spliwise.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.core.Stats
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.data.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Every transaction, grouped by day with day totals; search + filters. */
@Composable
fun ActivityScreen(repo: Repo, cats: Map<String, Category>, onStatements: () -> Unit, onEdit: (Txn) -> Unit) {
    val all by remember { repo.recent(2000) }.collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var month by remember { mutableStateOf<java.time.YearMonth?>(java.time.YearMonth.now()) } // null = all time
    val months = remember(all) { (all.map { java.time.YearMonth.from(Dates.localDate(it.ts)) } + java.time.YearMonth.now()).distinct().sortedDescending() }
    val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    val shown = all.filter { t ->
        (month == null || java.time.YearMonth.from(Dates.localDate(t.ts)) == month) &&
        (filter == "all" ||(filter == "out" && !t.isCredit) || (filter == "in" && t.isCredit) || t.category == filter) &&
            (q.isBlank() || listOf(t.merchant, t.note, cats.of(t.category).name).any { it.contains(q, true) } ||
                Money.format(t.amount).contains(q))
    }
    val days = shown.groupBy { Dates.localDate(it.ts) }
    val fmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header(P.Sunset) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Activity", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Pill("📄 Check statement", modifier = Modifier.clickable(onClick = onStatements))
                }
                Text("${month?.format(monthFmt) ?: "All time"} · ${shown.size} transactions · ${Money.format(Stats.spent(shown), decimals = false)} out",
                    color = Color.White.copy(alpha = .85f))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    q, { q = it }, placeholder = { Text("Search shop, note, amount…", color = Color.White.copy(alpha = .7f)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .5f), cursorColor = Color.White,
                    ),
                )
            }
        }
        item {
            // Month filter
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(months, key = { it.toString() }) { m -> Chip("📅 ${m.format(monthFmt)}", month == m, P.Orange) { month = m } }
                item { Chip("All time", month == null, P.Orange) { month = null } }
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("All", filter == "all") { filter = "all" } }
                item { Chip("Spent", filter == "out", P.Pink) { filter = "out" } }
                item { Chip("Received", filter == "in", P.Green) { filter = "in" } }
                items(cats.values.toList(), key = { it.id }) { c -> Chip("${c.emoji} ${c.name}", filter == c.id, c.c()) { filter = c.id } }
            }
        }
        if (shown.isEmpty()) item { Empty("🔍", "Nothing matches") }
        days.forEach { (day, list) ->
            item(key = "d$day") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)) {
                    Text(
                        when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(fmt) },
                        fontWeight = FontWeight.Bold, color = P.Ink, modifier = Modifier.weight(1f),
                    )
                    Text("−" + Money.format(Stats.spent(list)), color = P.Sub, fontSize = 13.sp)
                }
            }
            items(list, key = { it.id }) { t -> Box(Modifier.padding(horizontal = 16.dp)) { TxnRow(t, cats) { onEdit(t) } } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

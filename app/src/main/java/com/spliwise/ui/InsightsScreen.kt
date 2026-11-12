package com.spliwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.core.Stats
import com.spliwise.data.Category
import com.spliwise.data.Repo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(repo: Repo, cats: Map<String, Category>, onLedger: () -> Unit, onEdit: (com.spliwise.data.Txn) -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var catOpen by remember { mutableStateOf<String?>(null) }
    var day by remember(ym) { mutableStateOf<Int?>(if (ym == YearMonth.now()) LocalDate.now().dayOfMonth - 1 else null) }
    val sixFrom = remember(ym) { Dates.monthRange(ym.minusMonths(5)).first }
    val to = remember(ym) { Dates.monthRange(ym).second }
    val six by remember(ym) { repo.range(sixFrom, to) }.collectAsState(emptyList())
    val monthStart = Dates.monthRange(ym).first
    val month = six.filter { it.ts >= monthStart }
    val prevStart = Dates.monthRange(ym.minusMonths(1)).first
    val prev = six.filter { it.ts in prevStart until monthStart }

    val spent = Stats.spent(month)
    val prevSpent = Stats.spent(prev)
    val perDay = Stats.byDay(month, ym)
    val daysSoFar = if (ym == YearMonth.now()) LocalDate.now().dayOfMonth else ym.lengthOfMonth()
    val byCat = Stats.byCategory(month)
    val trend = Stats.byMonth(six, ym, 6)
    val dayTxns = day?.let { d -> month.filter { Dates.localDate(it.ts).dayOfMonth == d + 1 } }.orEmpty()
    val fmtM = DateTimeFormatter.ofPattern("MMMM yyyy")

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header(P.Grape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("‹") { ym = ym.minusMonths(1) }
                    Text(ym.format(fmtM), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Arrow("›", enabled = ym < YearMonth.now()) { ym = ym.plusMonths(1) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Total spent", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                AnimatedMoney(spent, MaterialTheme.typography.displaySmall, Color.White)
                Spacer(Modifier.height(10.dp))
                Row {
                    Stat("Avg / day", Money.format(spent / daysSoFar.coerceAtLeast(1), decimals = false), Modifier.weight(1f))
                    Stat("Income ›", Money.format(Stats.income(month), decimals = false), Modifier.weight(1f).clickable(onClick = onLedger))
                    val diff = if (prevSpent > 0) ((spent - prevSpent) * 100 / prevSpent).toInt() else null
                    Stat("vs last month", diff?.let { (if (it > 0) "▲ " else "▼ ") + "${kotlin.math.abs(it)}%" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Title("Day by day")
                SCard {
                    BarChart(
                        perDay.toList(), (1..perDay.size).map { "$it" }, day, labelEvery = 5,
                        onSelect = { day = it },
                    )
                    day?.let { d ->
                        Spacer(Modifier.height(10.dp))
                        val top = Stats.byCategory(dayTxns).firstOrNull()
                        Text(
                            "${d + 1} ${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} · ${Money.format(perDay[d])}" +
                                (top?.let { " · most on ${cats.of(it.first).emoji} ${cats.of(it.first).name}" } ?: ""),
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        )
                    }
                }
                Title("Where the money went")
                SCard {
                    if (byCat.isEmpty()) Empty("🌱", "No spends in this month")
                    else {
                        Box(Modifier.align(Alignment.CenterHorizontally)) {
                            Donut(byCat.map { cats.of(it.first).c() to it.second }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${byCat.size}", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                                    Text("categories", color = P.Sub, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        byCat.forEach { (id, v) ->
                            val c = cats.of(id)
                            Row(
                                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { catOpen = id }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CatBubble(c, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(c.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                        Text("${Money.format(v, decimals = false)} · ${Stats.percent(v, spent)}%", fontSize = 13.sp, color = P.Sub)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (c.budget > 0) {
                                        BudgetBar(v, c.budget, c.c())
                                        Text("${Stats.percent(v, c.budget)}% of ${Money.format(c.budget, decimals = false)} budget",
                                            fontSize = 11.sp, color = if (v > c.budget) P.Red else P.Sub)
                                    } else ShareBar(v.toFloat() / spent.coerceAtLeast(1), c.c())
                                }
                                Text("  ›", color = P.Sub, fontSize = 20.sp)
                            }
                        }
                    }
                }
                Title("Spending calendar")
                SCard {
                    Heatmap(ym, perDay, day) { day = if (day == it) null else it }
                    day?.let { d ->
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d + 1} ${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${dayTxns.count { !it.isCredit }} spends · ${Money.format(perDay[d])}", color = P.Sub, fontSize = 13.sp)
                        }
                        if (dayTxns.isEmpty()) Text("No spends on this day 🎉", color = P.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        dayTxns.forEach { t -> TxnRow(t, cats) { onEdit(t) } }
                    }
                }
                Title("Last 6 months")
                SCard {
                    BarChart(trend.map { it.second }, trend.map { it.first.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }, 5, height = 130.dp)
                }
                val top = Stats.topMerchants(month)
                if (top.isNotEmpty()) {
                    Title("Top places")
                    SCard {
                        top.forEachIndexed { i, (m, v) ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", fontWeight = FontWeight.ExtraBold, color = listOf(P.Pink, P.Violet, P.Teal, P.Orange, P.Sky)[i], fontSize = 18.sp, modifier = Modifier.width(26.dp))
                                Text(m, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(Money.format(v, decimals = false), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Tap a category -> every transaction of it in this month
    catOpen?.let { id ->
        val c = cats.of(id)
        val list = month.filter { it.category == id && !it.isCredit }
        CategorySheet(c, ym, list, cats, onClose = { catOpen = null }) { catOpen = null; onEdit(it) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    c: Category, ym: YearMonth, list: List<com.spliwise.data.Txn>, cats: Map<String, Category>,
    onClose: () -> Unit, onEdit: (com.spliwise.data.Txn) -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatBubble(c, 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year} · ${list.size} payments", color = P.Sub, fontSize = 13.sp)
                }
                Text(Money.format(list.sumOf { it.amount }), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(list.size) { i -> TxnRow(list[i], cats) { onEdit(list[i]) } }
            }
        }
    }
}

@Composable
private fun Arrow(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) .25f else .08f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

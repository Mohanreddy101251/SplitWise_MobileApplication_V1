package com.spliwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.auth.Profile
import com.spliwise.classify.Categories
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.core.Stats
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.data.Txn
import com.spliwise.split.Ledger
import com.spliwise.split.SplitRow
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HomeScreen(
    repo: Repo, settings: com.spliwise.ingest.BankSettings, profile: Profile, cats: Map<String, Category>,
    onAdd: (String) -> Unit, onEdit: (Txn) -> Unit, onReview: () -> Unit, onSettings: () -> Unit,
    onSplits: () -> Unit, onInsights: () -> Unit, onLedger: () -> Unit,
) {
    val ym = remember { YearMonth.now() }
    val (from, to) = remember(ym) { Dates.monthRange(ym) }
    val month by remember(from) { repo.range(from, to) }.collectAsState(emptyList())
    val reviewCount by repo.reviewCount.collectAsState(0)
    val splits by repo.splits.collectAsState(emptyList())
    val shares by repo.shares.collectAsState(emptyList())
    val settles by repo.settlements.collectAsState(emptyList())

    val todayStart = Dates.dayStart(LocalDate.now())
    val today = month.filter { it.ts >= todayStart }
    val spentToday = Stats.spent(today)
    val spentMonth = Stats.spent(month)
    val budget = cats.values.sumOf { it.budget }
    val todayTop = Stats.byCategory(today).firstOrNull()
    val monthCats = Stats.byCategory(month)
    val owed = remember(splits, shares, settles) {
        val byId = shares.groupBy { it.splitId }
        Ledger.balances(splits.map { s -> SplitRow(s.payerId, byId[s.id].orEmpty().map { it.personId to it.amount }) }, settles.map { it.personId to it.amount })
    }
    val getBack = owed.values.filter { it > 0 }.sum()
    val iOwe = -owed.values.filter { it < 0 }.sum()
    val greet = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greet,", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                        Text("${profile.name} ✨", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f)).clickable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) { Text("⚙️", fontSize = 20.sp) }
                }
                Spacer(Modifier.height(18.dp))
                Text("Spent today", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                AnimatedMoney(spentToday, MaterialTheme.typography.displaySmall, Color.White)
                Spacer(Modifier.height(14.dp))
                Row {
                    Stat("This month", Money.format(spentMonth, decimals = false), Modifier.weight(1f))
                    Stat("Income ›", Money.format(Stats.income(month), decimals = false), Modifier.weight(1f).clickable(onClick = onLedger))
                    if (budget > 0) Stat("Budget left", Money.format(budget - spentMonth, decimals = false), Modifier.weight(1f))
                }
                if (budget > 0) {
                    Spacer(Modifier.height(10.dp))
                    BudgetBar(spentMonth, budget, Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-14).dp)) {
                if (reviewCount > 0) {
                    SCard(onClick = onReview, pad = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔔", fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$reviewCount payment${if (reviewCount > 1) "s" else ""} need a category", fontWeight = FontWeight.Bold)
                                Text("Tap once — I'll remember the shop next time", color = P.Sub, fontSize = 12.sp)
                            }
                            Text("›", fontSize = 26.sp, color = P.Orange)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // Where did today's money go?
                SCard(onClick = onInsights) {
                    if (todayTop == null) {
                        Text("No spends yet today 🎉", fontWeight = FontWeight.Bold)
                        Text("Tap + to add, hold + to say “grocery 100”.", color = P.Sub, fontSize = 13.sp)
                    } else {
                        val c = cats.of(todayTop.first)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CatBubble(c, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Most of today went to", color = P.Sub, fontSize = 12.sp)
                                Text("${c.name} · ${Money.format(todayTop.second)}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Pill("${Stats.percent(todayTop.second, spentToday)}%", bg = c.c(), fg = Color.White)
                        }
                    }
                }
            }
        }
        item {
            Title("Quick add", Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cats.values.filter { it.id != Categories.UNKNOWN && it.id != Categories.INCOME }.toList(), key = { it.id }) { c ->
                    Column(
                        Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(c.c().copy(alpha = .20f), c.c().copy(alpha = .08f))))
                            .clickable { onAdd(c.id) }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(c.emoji, fontSize = 26.sp)
                        Text(c.name.substringBefore(' '), fontSize = 11.sp, color = P.Ink, maxLines = 1)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Title("This month by category", action = "Insights ›", onAction = onInsights)
                SCard {
                    if (monthCats.isEmpty()) Empty("🧾", "Nothing spent this month yet")
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(monthCats.take(6).map { cats.of(it.first).c() to it.second }, size = 130.dp, stroke = 18.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Money.format(spentMonth, decimals = false), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                Text(ym.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3), color = P.Sub, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            monthCats.take(6).forEach { (id, v) ->
                                val c = cats.of(id)
                                Legend(c.c(), "${c.emoji} ${c.name}", "${Stats.percent(v, spentMonth)}%")
                            }
                        }
                    }
                }
                if (getBack > 0 || iOwe > 0) {
                    Spacer(Modifier.height(12.dp))
                    SCard(onClick = onSplits) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("You'll get back", color = P.Sub, fontSize = 12.sp)
                                Text(Money.format(getBack), color = P.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("You owe", color = P.Sub, fontSize = 12.sp)
                                Text(Money.format(iOwe), color = P.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text("🤝", fontSize = 28.sp)
                        }
                    }
                }
                Title("Recent")
            }
        }
        val recent = month.take(12)
        if (recent.isEmpty()) item { Empty("🪙", "Your spends will show up here") }
        items(recent, key = { it.id }) { t ->
            Box(Modifier.padding(horizontal = 16.dp)) { TxnRow(t, cats) { onEdit(t) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

package com.spliwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.spliwise.classify.Categories
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.core.Stats
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.data.Txn
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * One month's money in and out. Everything here is the same data the whole app uses:
 * edit or add an entry and Home, Insights, Activity and budgets update automatically.
 */
@Composable
fun LedgerScreen(repo: Repo, cats: Map<String, Category>, onBack: () -> Unit, onEdit: (Txn) -> Unit, onAdd: (String) -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.now()) }
    val (from, to) = remember(ym) { Dates.monthRange(ym) }
    val month by remember(ym) { repo.range(from, to) }.collectAsState(emptyList())
    var open by remember { mutableStateOf<String?>(null) }

    val income = month.filter { it.isCredit && it.category !in Categories.TRANSFERS }
    val spends = month.filter { !it.isCredit && it.category !in Categories.TRANSFERS }
    val moved = month.filter { it.category in Categories.TRANSFERS }
    val inTotal = Stats.income(month)
    val outTotal = Stats.spent(month)
    val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header(P.Hero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Circle("‹", onClick = onBack)
                    Text("Income & Spent", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 12.dp).weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Circle("‹") { ym = ym.minusMonths(1) }
                    Text(ym.format(fmt), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Circle("›", enabled = ym < YearMonth.now()) { ym = ym.plusMonths(1) }
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    Stat("Income", Money.format(inTotal, decimals = false), Modifier.weight(1f))
                    Stat("Spent", Money.format(outTotal, decimals = false), Modifier.weight(1f))
                    Stat(if (inTotal >= outTotal) "Saved" else "Overspent", Money.format(kotlin.math.abs(inTotal - outTotal), decimals = false), Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Title("💰 Income", action = "＋ Add income", onAction = { onAdd(Categories.INCOME) })
                SCard {
                    if (income.isEmpty()) Text("No income recorded this month. Tap “＋ Add income”.", color = P.Sub, fontSize = 13.sp)
                    income.forEach { t -> TxnRow(t, cats) { onEdit(t) } }
                    if (income.isNotEmpty()) TotalLine("Total income", inTotal, P.Green)
                }

                Title("🧾 Spent", action = "＋ Add spend", onAction = { onAdd("food") })
                SCard {
                    val byCat = Stats.byCategory(month)
                    if (byCat.isEmpty()) Text("Nothing spent this month.", color = P.Sub, fontSize = 13.sp)
                    byCat.forEach { (id, v) ->
                        val c = cats.of(id)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = if (open == id) null else id }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatBubble(c, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${Money.format(v)}  ${if (open == id) "▲" else "▼"}", fontWeight = FontWeight.Bold)
                        }
                        if (open == id) spends.filter { it.category == id }.forEach { t ->
                            Box(Modifier.padding(start = 12.dp)) { TxnRow(t, cats) { onEdit(t) } }
                        }
                    }
                    if (byCat.isNotEmpty()) TotalLine("Total spent", outTotal, P.Pink)
                }

                if (moved.isNotEmpty()) {
                    Title("💳 Not counted (card bill & refunds)")
                    SCard {
                        Text("Paying a credit-card bill, or money credited back to a card, only moves money. It is shown here but not counted as income or spend.",
                            color = P.Sub, fontSize = 12.sp)
                        moved.forEach { t -> TxnRow(t, cats) { onEdit(t) } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, v: Long, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(Money.format(v), fontWeight = FontWeight.ExtraBold, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Circle(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) .25f else .08f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

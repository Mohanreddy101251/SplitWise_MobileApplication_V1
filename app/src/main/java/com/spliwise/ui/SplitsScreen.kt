package com.spliwise.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.core.Money
import com.spliwise.data.Person
import com.spliwise.data.Repo
import com.spliwise.data.Split
import com.spliwise.split.Ledger
import com.spliwise.split.ME
import com.spliwise.split.SplitEngine
import com.spliwise.split.SplitRow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Simple splits: amount + what for -> how many people -> names (optional, else Person 1, 2…) -> equal shares.
 * Every split can be edited later (tap ✎ in History).
 */
@Composable
fun SplitsScreen(repo: Repo, onCommand: (String) -> Unit, onVoice: () -> Unit, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val people by repo.people.collectAsState(emptyList())
    val splits by repo.splits.collectAsState(emptyList())
    val shares by repo.shares.collectAsState(emptyList())
    val settles by repo.settlements.collectAsState(emptyList())
    val byId = shares.groupBy { it.splitId }
    val bal = remember(splits, shares, settles) {
        Ledger.balances(splits.map { s -> SplitRow(s.payerId, byId[s.id].orEmpty().map { it.personId to it.amount }) }, settles.map { it.personId to it.amount })
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fmt = DateTimeFormatter.ofPattern("d MMM")

    // ----- form state -----
    var editing by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    val names = remember { mutableStateListOf("Me", "") }
    var cmd by remember { mutableStateOf("") }
    var settleFor by remember { mutableStateOf<Pair<Person, Long>?>(null) }
    var confirmDelete by remember { mutableStateOf<Split?>(null) }

    fun resize(n: Int) { while (names.size < n) names.add(""); while (names.size > n) names.removeAt(names.lastIndex) }
    fun reset() { editing = null; amount = ""; what = ""; names.clear(); names.addAll(listOf("Me", "")) }
    fun edit(s: Split) {
        val parts = byId[s.id].orEmpty().sortedBy { it.idx }
        editing = s.id; amount = (s.total / 100.0).toBigDecimal().stripTrailingZeros().toPlainString(); what = s.title
        names.clear(); names.addAll(parts.map { if (it.personId == ME) "Me" else if (it.personId == null) "" else it.label })
        scope.launch { listState.animateScrollToItem(1) }
    }

    val total = Money.parse(amount) ?: 0L
    val n = names.size
    val each = if (total > 0 && n > 0) SplitEngine.equal(total, n) else emptyList()
    val error = when {
        amount.isNotBlank() && total <= 0 -> "Enter an amount more than ₹0"
        n < 2 -> "A split needs at least 2 people"
        names.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.let { it.size != it.distinct().size } -> "The same name is used twice"
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Header(P.Ocean) {
                Text("Splits", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row {
                    Stat("You'll get back", Money.format(bal.values.filter { it > 0 }.sum()), Modifier.weight(1f))
                    Stat("You owe", Money.format(-bal.values.filter { it < 0 }.sum()), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        cmd, { cmd = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("or say: split 300 with ravi raju", color = Color.White.copy(alpha = .7f)) },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (cmd.isNotBlank()) { onCommand(cmd); cmd = "" } }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .5f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable {
                        if (cmd.isNotBlank()) { onCommand(if ("split" in cmd.lowercase() || "each" in cmd.lowercase()) cmd else "split $cmd"); cmd = "" } else onVoice()
                    }, contentAlignment = Alignment.Center) { Text(if (cmd.isBlank()) "🎙️" else "➤", fontSize = 22.sp, color = P.Teal) }
                }
            }
        }

        // ----- 1-2-3 form -----
        item {
            Box(Modifier.padding(16.dp)) {
                SCard {
                    Text(if (editing == null) "New split" else "✎ Edit split", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Step("1", "How much, and for what?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("₹ Amount") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(what, { what = it.take(40) }, label = { Text("For (dinner, cab…)") },
                            singleLine = true, modifier = Modifier.weight(1.3f), shape = RoundedCornerShape(14.dp))
                    }
                    Step("2", "How many people?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBtn("−", enabled = n > 2) { resize(n - 1) }
                        Text("$n", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 22.dp))
                        RoundBtn("+", enabled = n < 30) { resize(n + 1) }
                        Spacer(Modifier.weight(1f))
                        if (each.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(each.first()), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = P.Teal)
                            Text("each", color = P.Sub, fontSize = 12.sp)
                        }
                    }
                    Step("3", "Names (optional)")
                    Text("Leave blank for Person 1, 2… · “Me” is you", color = P.Sub, fontSize = 12.sp)
                    names.forEachIndexed { i, name ->
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name.ifBlank { "${i + 1}" }, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(name, { names[i] = it.take(24) }, singleLine = true, modifier = Modifier.weight(1f),
                                placeholder = { Text("Person ${i + 1}") }, shape = RoundedCornerShape(14.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(each.getOrNull(i)?.let { Money.format(it) } ?: "—", fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
                        }
                    }
                    // Quick-fill from saved people
                    val unused = people.filter { p -> names.none { it.trim().equals(p.name, true) } }
                    if (unused.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(unused, key = { it.id }) { p ->
                            Chip("+ ${p.name}", false, P.Teal) {
                                val slot = names.indexOfFirst { it.isBlank() }
                                if (slot >= 0) names[slot] = p.name else names.add(p.name)
                            }
                        }
                    }
                    error?.let { Text("⚠ $it", color = P.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (editing != null) GradientButton("Cancel", Modifier.weight(1f), brush = P.Grape) { reset() }
                        GradientButton(if (editing == null) "Split ${if (total > 0) Money.format(total) else ""}" else "Update split",
                            Modifier.weight(2f), brush = P.Ocean, enabled = total > 0 && error == null) {
                            val wasEdit = editing != null
                            val title = what.trim().ifBlank { "Split" }
                            scope.launch {
                                repo.saveSimpleSplit(editing, title, total, names.toList())
                                reset()
                                snack.showSnackbar(if (wasEdit) "Split updated" else "Split ${Money.format(total)} between $n", duration = SnackbarDuration.Short)
                            }
                        }
                    }
                }
            }
        }

        // ----- balances -----
        val owing = people.filter { (bal[it.id] ?: 0L) != 0L }
        item { Title("Who owes whom", Modifier.padding(horizontal = 16.dp)) }
        if (owing.isEmpty()) item { Empty("✅", "Everyone is settled up") }
        items(owing, key = { "p${it.id}" }) { p ->
            val b = bal[p.id] ?: 0L
            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                SCard(pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(if (b > 0) "owes you ${Money.format(b)}" else "you owe ${Money.format(-b)}",
                                color = if (b > 0) P.Green else P.Red, fontSize = 13.sp)
                        }
                        Pill("Remind", bg = P.Teal.copy(alpha = .14f), fg = P.Teal, modifier = Modifier.clickable { remind(ctx, p, b) })
                        Spacer(Modifier.width(6.dp))
                        Pill("Settle", bg = P.Violet.copy(alpha = .12f), fg = P.Violet, modifier = Modifier.clickable { settleFor = p to b })
                    }
                }
            }
        }

        // ----- history (editable) -----
        item { Title("History", Modifier.padding(horizontal = 16.dp)) }
        if (splits.isEmpty()) item { Empty("🧾", "No splits yet") }
        items(splits, key = { "s${it.id}" }) { s ->
            val parts = byId[s.id].orEmpty().sortedBy { it.idx }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                SCard(pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold)
                            Text("${parts.size} people · ${java.time.Instant.ofEpochMilli(s.ts).atZone(java.time.ZoneId.systemDefault()).format(fmt)}",
                                color = P.Sub, fontSize = 12.sp)
                        }
                        Text(Money.format(s.total), fontWeight = FontWeight.ExtraBold)
                    }
                    Text(parts.joinToString("  ·  ") { "${it.label} ${Money.format(it.amount)}" }, color = P.Sub, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("✎ Edit", bg = P.Violet.copy(alpha = .12f), fg = P.Violet, modifier = Modifier.clickable { edit(s) })
                        Pill("🗑 Delete", bg = P.Red.copy(alpha = .10f), fg = P.Red, modifier = Modifier.clickable { confirmDelete = s })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    settleFor?.let { (p, b) ->
        AlertDialog(
            onDismissRequest = { settleFor = null },
            title = { Text("Settle with ${p.name}") },
            text = { Text(if (b > 0) "${p.name} paid you ${Money.format(b)}?" else "You paid ${p.name} ${Money.format(-b)}?") },
            confirmButton = { TextButton({ scope.launch { repo.settle(p.id, b) }; settleFor = null }) { Text("Yes, settled") } },
            dismissButton = {
                Row {
                    if (b < 0 && p.upi != null) TextButton({ payUpi(ctx, p, -b) }) { Text("Pay via UPI") }
                    TextButton({ settleFor = null }) { Text("Cancel") }
                }
            },
        )
    }
    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${s.title}”?") },
            text = { Text("${Money.format(s.total)} split will be removed and balances updated.") },
            confirmButton = { TextButton({ scope.launch { repo.deleteSplit(s.id); if (editing == s.id) reset() }; confirmDelete = null }) { Text("Delete", color = P.Red) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(P.Ocean), contentAlignment = Alignment.Center) {
            Text(n, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("  $text", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundBtn(t: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (enabled) P.Teal.copy(alpha = .15f) else P.Line).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (enabled) P.Teal else P.Sub) }
}

private fun remind(ctx: android.content.Context, p: Person, b: Long) {
    val msg = if (b > 0) "Hi ${p.name}, gentle reminder: you owe me ${Money.format(b)} (from SpliWise) 🙂"
    else "Hi ${p.name}, I owe you ${Money.format(-b)} — sending it soon!"
    val wa = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg).setPackage("com.whatsapp")
    runCatching { ctx.startActivity(wa) }.onFailure {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Remind ${p.name}"))
    }
}

private fun payUpi(ctx: android.content.Context, p: Person, paise: Long) {
    val uri = Uri.parse("upi://pay").buildUpon().appendQueryParameter("pa", p.upi).appendQueryParameter("pn", p.name)
        .appendQueryParameter("am", "%.2f".format(paise / 100.0)).appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "SpliWise settle-up").build()
    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay ${p.name}")) }
}

package com.spliwise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.auth.Profile
import com.spliwise.classify.Categories
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.data.Source
import com.spliwise.data.Txn
import com.spliwise.ingest.BankSettings
import com.spliwise.voice.CommandParser
import com.spliwise.voice.rememberVoice
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab(val emoji: String, val label: String) { HOME("🏠", "Home"), ACTIVITY("📜", "Activity"), INSIGHTS("📊", "Insights"), SPLITS("🤝", "Splits") }

/** What the add/edit sheet should show. */
data class Sheet(val preset: String? = null, val edit: Txn? = null)

@Composable
fun MainApp(repo: Repo, settings: BankSettings, profile: Profile, openReview: Boolean, onReviewOpened: () -> Unit, onLock: () -> Unit) {
    val cats by repo.categories.collectAsState(emptyList())
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by rememberSaveable { mutableStateOf<String?>(null) } // "review" | "settings"
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var problem by remember { mutableStateOf<Pair<String, String>?>(null) } // heard, message
    var pendingSplit by remember { mutableStateOf<Pair<CommandParser.Split, List<String>>?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(openReview) { if (openReview) { page = "review"; onReviewOpened() } }
    LaunchedEffect(repo) { repo.ensureCategories() }

    fun runCommand(heard: String) {
        when (val r = CommandParser.parse(heard)) {
            is CommandParser.Error -> problem = heard to r.message
            is CommandParser.Expense -> scope.launch {
                val id = repo.add(r.amount, r.category, r.note, System.currentTimeMillis(), Source.VOICE)
                val name = Categories.defaults.firstOrNull { it.id == r.category }?.let { "${it.emoji} ${it.name}" } ?: r.category
                if (snack.showSnackbar("Added ${Money.format(r.amount)} to $name", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                    repo.get(id)?.let { repo.delete(it) }
            }
            is CommandParser.Split -> scope.launch {
                val missing = repo.missingPeople(r.names)
                if (missing.isNotEmpty()) pendingSplit = r to missing
                else saveSplit(repo, r, false, snack) { tab = Tab.SPLITS }
            }
        }
    }
    val voice = rememberVoice(onText = ::runCommand, onError = { problem = "" to it })

    BackHandler(page != null) { page = null }
    BackHandler(page == null && tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        containerColor = P.Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (page == null) BottomBar(tab, onTab = { tab = it }, onAdd = { sheet = Sheet() }, onVoice = { voice.start() })
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            when (page) {
                "review" -> ReviewScreen(repo, catMap) { page = null }
                "settings" -> SettingsScreen(repo, settings, profile, catMap, onBack = { page = null }, onLock = onLock,
                    onStatements = { page = "statements" })
                "statements" -> StatementScreen(repo) { page = null }
                "ledger" -> LedgerScreen(repo, catMap, onBack = { page = null }, onEdit = { sheet = Sheet(edit = it) },
                    onAdd = { sheet = Sheet(preset = it) })
                else -> when (tab) {
                    Tab.HOME -> HomeScreen(repo, settings, profile, catMap,
                        onAdd = { sheet = Sheet(preset = it) }, onEdit = { sheet = Sheet(edit = it) },
                        onReview = { page = "review" }, onSettings = { page = "settings" },
                        onSplits = { tab = Tab.SPLITS }, onInsights = { tab = Tab.INSIGHTS }, onLedger = { page = "ledger" })
                    Tab.ACTIVITY -> ActivityScreen(repo, catMap, onStatements = { page = "statements" }) { sheet = Sheet(edit = it) }
                    Tab.INSIGHTS -> InsightsScreen(repo, catMap, onLedger = { page = "ledger" }) { sheet = Sheet(edit = it) }
                    Tab.SPLITS -> SplitsScreen(repo, onCommand = ::runCommand, onVoice = { voice.start() }, snack = snack)
                }
            }
        }
    }

    sheet?.let { s -> AddSheet(repo, cats, s, onVoice = { sheet = null; voice.start() }) { sheet = null } }
    if (voice.listening) VoiceOverlay(voice.partial) { voice.stop() }

    problem?.let { (heard, msg) ->
        AlertDialog(
            onDismissRequest = { problem = null },
            confirmButton = { TextButton({ problem = null; voice.start() }) { Text("Try again") } },
            dismissButton = { TextButton({ problem = null }) { Text("Close") } },
            title = { Text("Hmm, not quite 🤔") },
            text = { Column { if (heard.isNotBlank()) Text("Heard: “$heard”", color = P.Sub, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); Text(msg) } },
        )
    }
    pendingSplit?.let { (cmd, missing) ->
        AlertDialog(
            onDismissRequest = { pendingSplit = null },
            confirmButton = { TextButton({ pendingSplit = null; scope.launch { saveSplit(repo, cmd, true, snack) { tab = Tab.SPLITS } } }) { Text("Add & split") } },
            dismissButton = { TextButton({ pendingSplit = null }) { Text("Cancel") } },
            title = { Text("New ${if (missing.size == 1) "person" else "people"}") },
            text = { Text("${missing.joinToString()} ${if (missing.size == 1) "isn't" else "aren't"} in your people list yet. Add and split ${Money.format(cmd.total)}?") },
        )
    }
}

private suspend fun saveSplit(repo: Repo, cmd: CommandParser.Split, create: Boolean, snack: SnackbarHostState, show: () -> Unit) {
    val id = repo.saveSplit(cmd, create)
    show()
    val each = cmd.labels.zip(cmd.shares).joinToString { (l, a) -> "$l ${Money.format(a)}" }
    if (snack.showSnackbar("Split ${Money.format(cmd.total)}: $each", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) repo.deleteSplit(id)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, onVoice: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = P.Violet.copy(alpha = .3f))
                .clip(RoundedCornerShape(26.dp)).background(Color.White),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { i, t ->
                if (i == 2) Spacer(Modifier.weight(1f))
                val sel = t == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onTab(t) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.emoji, fontSize = if (sel) 22.sp else 19.sp)
                    Text(t.label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) P.Violet else P.Sub)
                }
            }
        }
        // Centre button: tap = add, long-press = voice
        Box(
            Modifier.align(Alignment.Center).size(62.dp)
                .shadow(16.dp, CircleShape, spotColor = P.Pink).clip(CircleShape).background(P.Hero)
                .combinedClickable(onClick = onAdd, onLongClick = onVoice),
            contentAlignment = Alignment.Center,
        ) { Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light) }
    }
}

@Composable
private fun VoiceOverlay(partial: String, onStop: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.25f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize().background(Color(0xCC1E1B3A)).clickable(onClick = onStop), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Box(Modifier.size(110.dp).scale(pulse).clip(CircleShape).background(P.Sunset), contentAlignment = Alignment.Center) {
                Text("🎙️", fontSize = 46.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text(partial.ifBlank { "Listening…" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Try “grocery 100”, “petrol 500”, “split 300 with Ravi and Raju”, “split 100 to 3”",
                color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(repo: Repo, cats: List<Category>, s: Sheet, onVoice: () -> Unit, onClose: () -> Unit) {
    val e = s.edit
    val pickable = cats.filter { it.id != Categories.UNKNOWN }
    var amount by remember { mutableStateOf(e?.let { (it.amount / 100.0).toBigDecimal().stripTrailingZeros().toPlainString() } ?: "") }
    var cat by remember { mutableStateOf(e?.category?.takeIf { it != Categories.UNKNOWN } ?: s.preset ?: "food") }
    var note by remember { mutableStateOf(e?.note ?: "") }
    val today = LocalDate.now()
    var date by remember { mutableStateOf(e?.let { Dates.localDate(it.ts) } ?: today) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val paise = Money.parse(amount) ?: 0L
    val color = cats.firstOrNull { it.id == cat }?.c() ?: P.Violet

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (e != null) "Edit" else if (cat == Categories.INCOME) "Add income" else "Add expense", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (e == null) Pill("🎙 Voice", bg = P.Pink.copy(alpha = .12f), fg = P.Pink, modifier = Modifier.clickable(onClick = onVoice))
            }
            e?.let { if (it.bank != null) Text("${it.merchant} · ${it.bank.uppercase()} ${it.account?.let { a -> "••$a" } ?: ""}", color = P.Sub, fontSize = 12.sp) }
            Text(
                "₹" + amount.ifEmpty { "0" }, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.height(176.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pickable, key = { it.id }) { c ->
                    val sel = c.id == cat
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) c.c().copy(alpha = .18f) else Color.Transparent)
                            .clickable { cat = c.id }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(c.emoji, fontSize = 22.sp)
                        Text(c.name.substringBefore(' '), fontSize = 10.sp, maxLines = 1, color = if (sel) P.Ink else P.Sub, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            OutlinedTextField(note, { note = it.take(60) }, placeholder = { Text("Note (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(14.dp))
            // Date: today, yesterday, or any earlier day (also changeable when editing)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Today", date == today) { date = today }
                Chip("Yesterday", date == today.minusDays(1)) { date = today.minusDays(1) }
                val other = date != today && date != today.minusDays(1)
                Chip(if (other) "📅 ${date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}" else "📅 Pick date", other) {
                    android.app.DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
            }
            NumPad { amount = applyKey(amount, it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (e != null) GradientButton("Delete", Modifier.weight(1f), brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(P.Red, P.Orange))) {
                    scope.launch { repo.delete(e); onClose() }
                }
                GradientButton(if (e == null) "Save ${if (paise > 0) Money.format(paise) else ""}" else "Update", Modifier.weight(2f), enabled = paise > 0) {
                    scope.launch {
                        // Today = now; another day = 8 PM that day (keeps the original time if the day didn't change)
                        fun tsFor(old: Long?) = when {
                            old != null && Dates.localDate(old) == date -> old
                            date == today -> System.currentTimeMillis()
                            else -> Dates.noon(date) + 8 * 3_600_000L
                        }
                        if (e == null) {
                            repo.add(paise, cat, note.trim(), tsFor(null), Source.MANUAL)
                        } else {
                            if (cat != e.category) repo.setCategory(e, cat, learn = e.merchant.isNotBlank())
                            val fresh = repo.get(e.id) ?: e
                            repo.update(fresh.copy(amount = paise, note = note.trim(), category = cat, needsReview = false, ts = tsFor(e.ts),
                                isCredit = if (cat == Categories.INCOME) true else fresh.isCredit && e.bank != null))
                        }
                        onClose()
                    }
                }
            }
        }
    }
}

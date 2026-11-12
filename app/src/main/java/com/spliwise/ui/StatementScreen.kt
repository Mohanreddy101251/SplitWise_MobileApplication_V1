package com.spliwise.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.data.Repo
import com.spliwise.ingest.Banks
import com.spliwise.ingest.GapReport
import com.spliwise.ingest.Importer
import com.spliwise.ingest.ParsedTxn
import com.spliwise.ingest.Rag
import com.spliwise.ingest.Reconciler
import com.spliwise.ingest.StatementInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private sealed interface UpState {
    data object Reading : UpState
    data class Locked(val error: String?) : UpState
    data object Ready : UpState
    data class Failed(val msg: String) : UpState
}

/** One uploaded PDF. Its bytes/text live only in memory while this screen is open, then are wiped. */
private class Upload(val name: String) {
    var bytes: ByteArray? = null
    var text: String? = null
    var state by mutableStateOf<UpState>(UpState.Reading)
    var info by mutableStateOf<StatementInfo?>(null)
    fun wipe() { bytes?.fill(0); bytes = null; text = null }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
private val SHORT = DateTimeFormatter.ofPattern("d MMM")

/**
 * Statement check: upload one or more bank / credit-card PDFs, unlock them (password used once, never saved),
 * pick a date range, and see only what is MISSING in SpliWise, coloured RED / AMBER / GREEN by money gap.
 */
@Composable
fun StatementScreen(repo: Repo, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploads = remember { mutableStateListOf<Upload>() }
    var from by remember { mutableStateOf(YearMonth.now().minusMonths(1).atEndOfMonth()) } // default: last month-end
    var to by remember { mutableStateOf(LocalDate.now()) }
    var reports by remember { mutableStateOf<List<Pair<Upload, GapReport>>?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { uploads.forEach { it.wipe() } } }

    fun readText(u: Upload, password: String) {
        val bytes = u.bytes ?: return
        scope.launch {
            u.state = UpState.Reading
            u.state = withContext(Dispatchers.IO) {
                try {
                    val text = Importer.pdfText(ctx, bytes, password)
                    val info = Reconciler.read(text, null)
                    u.text = text; u.info = info
                    if (info.rows.isEmpty()) UpState.Failed("No transactions found — is this a bank statement?") else UpState.Ready
                } catch (e: Importer.LockedPdf) {
                    UpState.Locked(if (password.isEmpty()) null else "Wrong password — try again")
                } catch (e: Exception) {
                    UpState.Failed("Couldn't read this PDF (${e.javaClass.simpleName})")
                }
            }
            reports = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            val u = Upload(displayName(ctx, uri))
            uploads += u
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull() }
                if (bytes == null) u.state = UpState.Failed("Couldn't open the file") else { u.bytes = bytes; readText(u, "") }
            }
        }
    }

    fun compare() {
        busy = true
        scope.launch {
            val ready = uploads.filter { it.state == UpState.Ready && it.info != null }
            val app = repo.db.txn().rangeOnce(Dates.dayStart(from.minusDays(2)), Dates.dayStart(to.plusDays(3)))
            val ignored = repo.ignoredKeys()
            reports = ready.map { it to Reconciler.compare(it.info!!, app, from, to, ignored) }
            busy = false
        }
    }

    fun act(rows: List<ParsedTxn>, add: Boolean) {
        scope.launch {
            if (add) repo.ingest(rows) else rows.forEach { repo.ignore(Reconciler.key(it)) }
            compare()
        }
    }

    val locked = uploads.firstOrNull { it.state is UpState.Locked }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header(P.Lime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center) { Text("‹", color = Color.White, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Statement check", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Find bank transactions missing in SpliWise", color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Title("1 · Upload statements")
                SCard {
                    Text("🔐 PDFs are read on this phone only. Passwords are used once to open the file and never saved.",
                        color = P.Sub, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("＋ Add PDF statements", Modifier.fillMaxWidth(), brush = P.Ocean) { picker.launch(arrayOf("application/pdf")) }
                    uploads.forEach { u -> UploadRow(u, onBank = { b -> u.text?.let { u.info = Reconciler.read(it, b); reports = null } },
                        onRemove = { u.wipe(); uploads.remove(u); reports = null }) }
                }

                Title("2 · Date range")
                SCard {
                    val cover = uploads.mapNotNull { it.info }.let { l -> l.mapNotNull { it.firstDay }.minOrNull() to l.mapNotNull { it.lastDay }.maxOrNull() }
                    if (cover.first != null) Text("Uploaded PDFs cover ${cover.first!!.format(DAY)} – ${cover.second!!.format(DAY)}", color = P.Sub, fontSize = 12.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateBox("From", from, Modifier.weight(1f)) { from = it; reports = null }
                        DateBox("To", to, Modifier.weight(1f)) { to = it; reports = null }
                    }
                    if (from > to) Text("⚠ “From” is after “To”", color = P.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    val canRun = uploads.any { it.state == UpState.Ready && it.info?.bankId != null } && from <= to && !busy
                    GradientButton(if (busy) "Comparing…" else "Compare with SpliWise", Modifier.fillMaxWidth(), enabled = canRun) { compare() }
                    if (uploads.any { it.state == UpState.Ready && it.info?.bankId == null })
                        Text("Pick the bank for each statement above first.", color = P.Orange, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        reports?.let { list ->
            item { Box(Modifier.padding(horizontal = 16.dp)) { Column { Title("3 · Gap report"); Summary(list.map { it.second }) } } }
            list.forEach { (u, r) ->
                item(key = "r-${u.name}-${u.hashCode()}") { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { ReportCard(u.name, r, scope, ::act) } }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    locked?.let { u -> PasswordDialog(u.name, (u.state as UpState.Locked).error,
        onOpen = { pw -> readText(u, pw) },
        onSkip = { u.state = UpState.Failed("Skipped — password needed"); u.wipe() }) }
}

@Composable
private fun UploadRow(u: Upload, onBank: (String) -> Unit, onRemove: () -> Unit) {
    val info = u.info
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when (u.state) { UpState.Reading -> "⏳"; is UpState.Locked -> "🔒"; UpState.Ready -> "✅"; is UpState.Failed -> "⚠️" }, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(u.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(
                    when (val s = u.state) {
                        UpState.Reading -> "Reading…"
                        is UpState.Locked -> "Password needed"
                        is UpState.Failed -> s.msg
                        UpState.Ready -> "${info!!.rows.size} transactions · ${info.firstDay?.format(SHORT)} – ${info.lastDay?.format(SHORT)}" +
                            (info.account?.let { " · ••$it" } ?: "") + if (info.isCard) " · Credit card" else ""
                    },
                    color = if (u.state is UpState.Failed) P.Red else P.Sub, fontSize = 12.sp,
                )
            }
            Text("✕", color = P.Sub, modifier = Modifier.clip(CircleShape).clickable(onClick = onRemove).padding(8.dp))
        }
        if (u.state == UpState.Ready) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Banks.all.forEach { b -> Chip(b.name, info?.bankId == b.id, P.Teal) { onBank(b.id) } }
        }
    }
}

@Composable
private fun DateBox(label: String, d: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(P.Violet.copy(alpha = .07f)).clickable {
            DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }.padding(12.dp)
    ) {
        Text(label, color = P.Sub, fontSize = 12.sp)
        Text("📅 ${d.format(DAY)}", fontWeight = FontWeight.Bold)
    }
}

private fun ragColor(r: Rag) = when (r) { Rag.GREEN -> P.Green; Rag.AMBER -> P.Orange; Rag.RED -> P.Red }

/** The bigger the gap, the stronger the red. */
private fun ragAlpha(gap: Long) = (0.35f + gap / 1_000_000f).coerceIn(0.35f, 1f) // full red from ₹10,000

@Composable
private fun Summary(list: List<GapReport>) {
    val gap = list.sumOf { it.gap }
    val missing = list.sumOf { it.missing.size }
    val rag = Reconciler.rag(gap)
    val c = ragColor(rag)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.copy(alpha = if (rag == Rag.RED) ragAlpha(gap) else .9f)).padding(18.dp)
    ) {
        Text(if (missing == 0) "All recorded ✅" else "${Money.format(gap)} not in SpliWise", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("$missing missing transaction${if (missing == 1) "" else "s"} across ${list.size} statement${if (list.size == 1) "" else "s"}",
            color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("🟢 nothing missing   🟠 up to ₹1,000   🔴 more than ₹1,000", color = Color.White.copy(alpha = .9f), fontSize = 11.sp)
    }
}

@Composable
private fun ReportCard(file: String, r: GapReport, scope: CoroutineScope, act: (List<ParsedTxn>, Boolean) -> Unit) {
    val c = ragColor(r.rag)
    val bank = Banks.byId(r.info.bankId)?.name ?: "Unknown bank"
    SCard(pad = 0.dp) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.copy(alpha = if (r.rag == Rag.RED) ragAlpha(r.gap) else 1f)))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$bank${r.info.account?.let { " ••$it" } ?: ""}${if (r.info.isCard) " · Credit card" else ""}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(file, color = P.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pill(when (r.rag) { Rag.GREEN -> "GREEN"; Rag.AMBER -> "AMBER"; Rag.RED -> "RED" }, bg = c, fg = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Line("📄 Statement", "${r.inStatement.size} txns · Out ${Money.format(r.stmtOut)} · In ${Money.format(r.stmtIn)}")
            Line("📱 In SpliWise", "${r.appTxns.size} txns · Out ${Money.format(r.appOut)} · In ${Money.format(r.appIn)}")
            if (r.bySource.isNotEmpty()) Text("      " + r.bySource.entries.joinToString(" · ") { "${Reconciler.sourceLabel(it.key)} ${it.value}" },
                color = P.Sub, fontSize = 11.sp)
            Line("⚠️ Missing", "${r.missing.size} txns · Out ${Money.format(r.missingOut)} · In ${Money.format(r.missingIn)}", c)
            if (r.ignoredCount > 0) Text("(${r.ignoredCount} ignored earlier, not shown)", color = P.Sub, fontSize = 11.sp)

            if (r.missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("Add all ${r.missing.size}", Modifier.weight(1f), brush = P.Ocean) { act(r.missing, true) }
                    GradientButton("Ignore all", Modifier.weight(1f), brush = P.Grape) { act(r.missing, false) }
                }
                r.missing.forEach { m -> MissingRow(m) { add -> act(listOf(m), add) } }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = P.Ink) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.width(112.dp), color = P.Sub)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun MissingRow(m: ParsedTxn, onAct: (Boolean) -> Unit) {
    Column(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(P.Bg).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.merchant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Dates.localDate(m.ts).format(DAY)} · ${if (m.isCredit) "Credited" else "Debited"}" +
                    (m.refNo?.let { " · Ref $it" } ?: ""), color = P.Sub, fontSize = 11.sp)
            }
            Text((if (m.isCredit) "+" else "−") + Money.format(m.amount), fontWeight = FontWeight.ExtraBold, color = if (m.isCredit) P.Green else P.Red)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("＋ Add to records", bg = P.Teal.copy(alpha = .15f), fg = P.Teal, modifier = Modifier.clickable { onAct(true) })
            Pill("Ignore", bg = P.Sub.copy(alpha = .12f), fg = P.Sub, modifier = Modifier.clickable { onAct(false) })
        }
    }
}

/** Password is held only in this dialog's state, passed once to the PDF reader, then cleared. */
@Composable
private fun PasswordDialog(file: String, error: String?, onOpen: (String) -> Unit, onSkip: () -> Unit) {
    var pw by remember(file, error) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔒 Password needed") },
        text = {
            Column {
                Text(file, color = P.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    pw, { pw = it }, singleLine = true, label = { Text("PDF password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                )
                error?.let { Text(it, color = P.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                Text("Used once to open the file. Not saved anywhere.", color = P.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton({ val p = pw; pw = ""; onOpen(p) }, enabled = pw.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton({ pw = ""; onSkip() }) { Text("Skip file") } },
    )
}

private fun displayName(ctx: android.content.Context, uri: Uri): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "statement.pdf"

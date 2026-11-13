package com.spliwise.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.spliwise.auth.Profile
import com.spliwise.auth.ProfileManager
import com.spliwise.classify.Categories
import com.spliwise.container
import com.spliwise.core.Money
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.ingest.AlarmScheduler
import com.spliwise.ingest.Bank
import com.spliwise.ingest.BankSettings
import com.spliwise.ingest.Banks
import com.spliwise.ingest.Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    repo: Repo, s: BankSettings, profile: Profile, cats: Map<String, Category>,
    onBack: () -> Unit, onLock: () -> Unit, onStatements: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) } // re-reads settings after a change
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val smsOk = remember(tick) { granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    fun askPerms() = perms.launch(buildList {
        add(Manifest.permission.READ_SMS); add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray())

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w ->
                    w.write("date,type,amount,category,merchant,note,source,bank,account,card\n")
                    repo.all().forEach { t ->
                        val d = java.time.Instant.ofEpochMilli(t.ts).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        fun q(x: String) = "\"" + x.replace("\"", "\"\"") + "\""
                        w.write("$d,${if (t.isCredit) "credit" else "debit"},${t.amount / 100.0},${q(cats.of(t.category).name)}," +
                            "${q(t.merchant)},${q(t.note)},${t.source},${t.bank ?: ""},${t.account ?: ""},${if (t.card) "yes" else ""}\n")
                    }
                }
            }
            info = "Exported to CSV"
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header(P.Hero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center) { Text("‹", color = Color.White, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Avatar(profile.name, 46.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(ProfileManager.mask(profile.phone), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                    }
                    Pill("🔒 Lock", modifier = Modifier.clickable(onClick = onLock))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Title("Bank statements")
                SCard(onClick = onStatements) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📄", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Check bank statements", fontWeight = FontWeight.Bold)
                            Text("Upload PDFs → see what's missing in SpliWise", color = P.Sub, fontSize = 12.sp)
                        }
                        Text("›", fontSize = 26.sp, color = P.Violet)
                    }
                }

                Title("Bank SMS import")
                SCard {
                    Text("🔐 This app has no internet permission: it cannot send anything off your phone.", fontSize = 13.sp, color = P.Sub)
                    Spacer(Modifier.height(10.dp))
                    if (!smsOk) GradientButton("Allow SMS & notifications", Modifier.fillMaxWidth(), brush = P.Sunset) { askPerms() }
                    else Text("✅ New bank alerts are added the moment they arrive", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("⏱ Every hour: the SMS inbox is re-checked for anything missed.", fontSize = 13.sp)
                    Text("🌙 11:50 PM: mini-statement SMS requests to your banks; replies read at 12:05 AM.", fontSize = 13.sp)
                    Text("Last run: ${remember(tick) { s.lastRun }}", fontSize = 12.sp, color = P.Sub)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton("Import now", Modifier.weight(1f), brush = P.Ocean, enabled = smsOk) {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Importer.fromInbox(ctx, repo, s, System.currentTimeMillis() - 30L * 86_400_000) }
                                info = "Scanned last 30 days of SMS: ${r.added} new, ${r.merged} already there, ${r.needReview.size} need a category."
                            }
                        }
                        GradientButton("Request now", Modifier.weight(1f), brush = P.Grape, enabled = smsOk) {
                            AlarmScheduler.runNow(ctx, sendRequests = true, collectAfterMin = 2)
                            info = "Mini-statement SMS sent to your banks. Replies are read in ~2 minutes."
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Accept test SMS from any number", fontSize = 14.sp)
                            Text("For testing with another phone. Keep off normally.", fontSize = 11.sp, color = P.Sub)
                        }
                        Switch(remember(tick) { s.allowBodyOnly }, { s.allowBodyOnly = it; tick++ })
                    }
                }
                Banks.all.forEach { b ->
                    androidx.compose.runtime.key(b.id) { Spacer(Modifier.height(10.dp)); BankCard(b, s) { tick++ } }
                }

                Title("Monthly budgets")
                Budgets(cats) { c, v -> scope.launch { repo.setBudget(c, v) } }

                Title("Backup")
                BackupCard(repo, s, profile) { info = it }

                Title("Data")
                SCard {
                    GradientButton("Export all to CSV", Modifier.fillMaxWidth(), brush = P.Lime) { csv.launch("spliwise-${java.time.LocalDate.now()}.csv") }
                    Spacer(Modifier.height(8.dp))
                    Text("Delete this profile", color = P.Red, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { confirmDelete = true }.padding(8.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    info?.let { AlertDialog({ info = null }, confirmButton = { TextButton({ info = null }) { Text("OK") } }, text = { Text(it) }) }
    if (confirmDelete) AlertDialog(
        { confirmDelete = false },
        title = { Text("Delete ${profile.name}'s profile?") },
        text = { Text("All expenses, splits and settings for ${ProfileManager.mask(profile.phone)} are permanently erased from this phone. This cannot be undone.") },
        confirmButton = { TextButton({ confirmDelete = false; ctx.container.profiles.delete(profile); onLock() }) { Text("Delete forever", color = P.Red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Daily encrypted backup to Downloads/SpliWise, protected by a backup password the app never stores. */
@Composable
private fun BackupCard(repo: Repo, bank: BankSettings, profile: Profile, onInfo: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = remember(profile.phone) { ctx.container.backup(profile.phone) }
    var tick by remember { mutableIntStateOf(0) }
    val on = remember(tick) { b.isOn }
    var editing by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a")

    fun backupNow() {
        busy = true
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { com.spliwise.backup.Backups.run(ctx, repo, profile.phone, profile.name, bank, b) }
                    .getOrElse { "Backup failed: ${it.message}" }
            }
            busy = false; tick++; onInfo(msg)
        }
    }

    SCard {
        if (on && !editing) {
            Text("✅ Daily backup is on", fontWeight = FontWeight.Bold)
            Text("Saved to Downloads/SpliWise, encrypted with your backup password. The last 7 days are kept.", color = P.Sub, fontSize = 12.sp)
            val last = remember(tick) { b.lastBackup }
            Text(
                if (last == 0L) "No backup yet" else "Last backup: " +
                    java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).format(fmt),
                fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(if (busy) "Saving…" else "Back up now", Modifier.weight(1f), brush = P.Ocean, enabled = !busy) { backupNow() }
                GradientButton("Change password", Modifier.weight(1f), brush = P.Grape) { editing = true }
            }
        } else {
            Text("Keep your data safe if the app is uninstalled", fontWeight = FontWeight.Bold)
            Text("A daily encrypted copy is saved in Downloads/SpliWise. After reinstalling, choose “Restore from backup” on the login screen; bank SMS since your last entry are then picked up automatically.",
                color = P.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pw, { pw = it }, label = { Text("Backup password (6+ characters)") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat password") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            val err = when {
                pw.isNotEmpty() && pw.length < com.spliwise.backup.BackupCrypto.MIN_PASSWORD -> "At least 6 characters"
                pw2.isNotEmpty() && pw != pw2 -> "Passwords don't match"
                else -> null
            }
            err?.let { Text(it, color = P.Red, fontSize = 12.sp) }
            Text("⚠ If you forget this password the backup can't be opened. The app never stores the password itself.",
                color = P.Orange, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) GradientButton("Cancel", Modifier.weight(1f), brush = P.Grape) { editing = false; pw = ""; pw2 = "" }
                GradientButton(if (busy) "Saving…" else "Turn on backup", Modifier.weight(2f), brush = P.Ocean,
                    enabled = !busy && err == null && pw.length >= com.spliwise.backup.BackupCrypto.MIN_PASSWORD && pw == pw2) {
                    val chars = pw.toCharArray(); pw = ""; pw2 = ""
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { b.setPassword(chars) }
                        editing = false; busy = false; tick++
                        backupNow()
                    }
                }
            }
        }
    }
}

@Composable
private fun BankCard(b: Bank, s: BankSettings, onChanged: () -> Unit) {
    var on by remember { mutableStateOf(s.enabled(b)) }
    var num by remember { mutableStateOf(s.requestNumber(b)) }
    var txt by remember { mutableStateOf(s.requestText(b)) }
    var expanded by remember { mutableStateOf(false) }
    SCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏦", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(b.name, fontWeight = FontWeight.Bold)
                Text(if (on) "SMS import on · tap to ${if (expanded) "hide" else "edit request"}" else "Off", color = P.Sub, fontSize = 12.sp)
            }
            Switch(on, { on = it; s.setEnabled(b, it); onChanged() })
        }
        if (expanded) {
            Text(b.requestNote, fontSize = 12.sp, color = P.Orange, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(num, { num = it }, label = { Text("Request SMS to") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(txt, { txt = it }, label = { Text("Message") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            GradientButton("Save", Modifier.fillMaxWidth()) { s.setRequest(b, num, txt); onChanged(); expanded = false }
        }
    }
}

/** Collapsed by default: one summary line; tap to open the per-category list. */
@Composable
private fun Budgets(cats: Map<String, Category>, onSet: (Category, Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val list = cats.values.filter { it.id != Categories.UNKNOWN && it.id != Categories.INCOME && it.id !in Categories.TRANSFERS }
    val set = list.filter { it.budget > 0 }
    SCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (set.isEmpty()) "No budgets set" else "${set.size} budgets · ${Money.format(set.sumOf { it.budget }, decimals = false)} / month",
                    fontWeight = FontWeight.SemiBold)
                Text(if (set.isEmpty()) "Tap to set a monthly limit per category" else set.joinToString("  ") { it.emoji + " " + Money.format(it.budget, decimals = false) },
                    color = P.Sub, fontSize = 12.sp, maxLines = 1)
            }
            Text(if (open) "▲" else "▼", color = P.Violet, fontSize = 16.sp)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            list.forEach { c -> BudgetRow(c) { onSet(c, it) } }
        }
    }
}

@Composable
private fun BudgetRow(c: Category, onSet: (Long) -> Unit) {
    var v by remember(c.budget) { mutableStateOf(if (c.budget > 0) (c.budget / 100).toString() else "") }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CatBubble(c, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(c.name, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedTextField(
            v, { v = it.filter(Char::isDigit).take(8); onSet((v.toLongOrNull() ?: 0) * 100) },
            singleLine = true, modifier = Modifier.width(120.dp), prefix = { Text("₹") }, placeholder = { Text("No limit", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp),
        )
    }
}

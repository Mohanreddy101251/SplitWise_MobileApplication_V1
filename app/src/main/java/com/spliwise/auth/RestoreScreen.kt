package com.spliwise.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.backup.BackupCrypto
import com.spliwise.backup.Backups
import com.spliwise.backup.Snapshot
import com.spliwise.container
import com.spliwise.core.Dates
import com.spliwise.ingest.SmsSyncWorker
import com.spliwise.ui.GradientButton
import com.spliwise.ui.P
import com.spliwise.ui.SCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * Reinstalled? 1) pick the .swb file from Downloads/SpliWise  2) type the backup password
 * 3) set a new PIN. Then all data comes back and bank SMS since the last entry are picked up.
 */
@Composable
fun RestoreFromBackup(pm: ProfileManager, onCancel: () -> Unit, onDone: (Profile) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var password by remember { mutableStateOf("") }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(canBiometric(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { Backups.readUri(ctx, uri) }.getOrNull() }
            when {
                bytes == null -> error = "Couldn't open that file"
                !BackupCrypto.isBackup(bytes) -> error = "That isn't a SpliWise backup (.swb) file"
                else -> { file = bytes; error = null }
            }
        }
    }

    SCard {
        Text("Restore from backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val s = snap
        when {
            file == null -> {
                Text("Pick your latest backup file. It's in Downloads → SpliWise, named like SpliWise-backup-4321-2026-11-13.swb",
                    color = P.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                GradientButton("Choose backup file", Modifier.fillMaxWidth(), brush = P.Ocean) { picker.launch(arrayOf("*/*")) }
            }
            s == null -> {
                Text("✅ Backup file selected. Enter the backup password you set in Settings.", color = P.Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Backup password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                GradientButton(if (busy) "Unlocking…" else "Unlock backup", Modifier.fillMaxWidth(), enabled = password.isNotEmpty() && !busy) {
                    busy = true
                    scope.launch {
                        val chars = password.toCharArray()
                        val result = withContext(Dispatchers.Default) { runCatching { Backups.decode(file!!, chars) } }
                        chars.fill(' ')
                        busy = false
                        result.onSuccess { snap = it; error = null }.onFailure {
                            error = when (it) {
                                is BackupCrypto.WrongPassword -> "Wrong backup password"
                                else -> "This backup couldn't be read (${it.javaClass.simpleName})"
                            }
                        }
                    }
                }
            }
            else -> {
                val last = s.lastTxnTs?.let { Dates.localDate(it).format(fmt) } ?: "—"
                Text("${s.name} · ${ProfileManager.mask(s.phone)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text("${s.txns.size} transactions · ${s.splits.size} splits · last entry $last", color = P.Sub, fontSize = 13.sp)
                Text("Set a new 4-digit PIN for this phone:", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Row {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, label = { Text("PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(4) }, label = { Text("Repeat") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                }
                if (canBiometric(ctx)) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Unlock with fingerprint", Modifier.weight(1f)); Switch(bio, { bio = it })
                }
                Spacer(Modifier.height(12.dp))
                GradientButton(if (busy) "Restoring…" else "Restore my data", Modifier.fillMaxWidth(),
                    enabled = pin.length == 4 && pin == pin2 && !busy) {
                    busy = true
                    scope.launch {
                        val chars = password.toCharArray()
                        val err = withContext(Dispatchers.IO) {
                            pm.create(s.name, s.phone, pin, bio) ?: try {
                                val c = ctx.container
                                c.repoFor(s.phone).restore(s)
                                c.settings(s.phone).apply { import(s.banks); lastSmsScan = Backups.resumePoint(s) }
                                c.backup(s.phone).setPassword(chars) // keep daily backups going with the same password
                                null
                            } catch (e: Exception) {
                                pm.all().firstOrNull { it.phone == s.phone }?.let(pm::delete) // don't leave a half-restored profile
                                "Restore failed (${e.javaClass.simpleName}). Nothing was changed; try again."
                            }
                        }
                        chars.fill(' '); password = ""
                        busy = false
                        if (err != null) { error = err; return@launch }
                        pm.setActive(s.phone)
                        SmsSyncWorker.runOnce(ctx) // pick up bank SMS since the last saved entry
                        onDone(pm.all().first { it.phone == s.phone })
                    }
                }
            }
        }
        error?.let { Text(it, color = P.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Text("Cancel", color = P.Violet, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onCancel).padding(12.dp))
    }
}

/** Shown if this profile's vault can't be unlocked on this phone. Offers a clean path to restore. */
@Composable
fun VaultProblem(p: Profile, onBack: () -> Unit, onDeleteAndRestore: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(P.Hero).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        SCard {
            Text("🔐 Can't unlock ${p.name}'s data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("The key that unlocks this profile's encrypted data is no longer on this phone. This can happen after an uninstall " +
                "or a phone reset. Your data can't be opened without it, but you can restore your latest backup file.",
                color = P.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            if (!confirm) GradientButton("Remove this profile & restore backup", Modifier.fillMaxWidth(), brush = P.Sunset) { confirm = true }
            else {
                Text("This removes the locked data for ${ProfileManager.mask(p.phone)} from this phone. Then choose “Restore from backup”.",
                    color = P.Red, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                GradientButton("Yes, remove and continue", Modifier.fillMaxWidth(), onClick = onDeleteAndRestore)
            }
            Text("Back", color = P.Violet, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onBack).padding(12.dp))
        }
    }
}

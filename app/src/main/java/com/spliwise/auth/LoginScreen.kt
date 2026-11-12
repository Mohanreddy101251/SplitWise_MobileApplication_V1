package com.spliwise.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.spliwise.ui.Avatar
import com.spliwise.ui.GradientButton
import com.spliwise.ui.NumPad
import com.spliwise.ui.P
import com.spliwise.ui.SCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface Mode {
    data object Pick : Mode
    data object Create : Mode
    data class Pin(val p: Profile) : Mode
}

@Composable
fun LoginScreen(pm: ProfileManager, onUnlocked: (Profile) -> Unit) {
    var profiles by remember { mutableStateOf(pm.all()) }
    var mode by remember { mutableStateOf<Mode>(if (profiles.isEmpty()) Mode.Create else Mode.Pick) }
    LaunchedEffect(Unit) {
        val a = pm.active()
        if (a != null && profiles.size == 1) mode = Mode.Pin(a)
    }

    Box(Modifier.fillMaxSize().background(P.Hero)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text("💸", fontSize = 54.sp)
            Text("SpliWise", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            Text("Track every rupee. Split every bill. 100% on your phone.", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            AnimatedContent(mode, label = "login") { m ->
                when (m) {
                    Mode.Pick -> PickProfile(profiles, onPick = { mode = Mode.Pin(it) }, onNew = { mode = Mode.Create })
                    Mode.Create -> CreateProfile(pm, canCancel = profiles.isNotEmpty(), onCancel = { mode = Mode.Pick }) {
                        profiles = pm.all(); mode = Mode.Pin(profiles.last())
                    }
                    is Mode.Pin -> PinEntry(pm, m.p, onBack = { mode = Mode.Pick }, onOk = onUnlocked)
                }
            }
        }
    }
}

@Composable
private fun PickProfile(list: List<Profile>, onPick: (Profile) -> Unit, onNew: () -> Unit) {
    SCard {
        Text("Who's using SpliWise?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        list.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(p) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    Text(ProfileManager.mask(p.phone), color = P.Sub, fontSize = 12.sp)
                }
                Text("›", fontSize = 24.sp, color = P.Violet)
            }
        }
        Spacer(Modifier.height(8.dp))
        GradientButton("+ Add another number", Modifier.fillMaxWidth(), brush = P.Sunset, onClick = onNew)
    }
}

@Composable
private fun CreateProfile(pm: ProfileManager, canCancel: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(canBiometric(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SCard {
        Text("Create your profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Your data is encrypted and stays on this phone.", color = P.Sub, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) }, label = { Text("Mobile number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Row {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, label = { Text("4-digit PIN") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(4) }, label = { Text("Repeat PIN") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
        }
        if (canBiometric(ctx)) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Unlock with fingerprint", Modifier.weight(1f))
            Switch(bio, { bio = it })
        }
        error?.let { Text(it, color = P.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) "Creating…" else "Create profile", Modifier.fillMaxWidth(), enabled = !busy) {
            if (pin != pin2) { error = "PINs don't match"; return@GradientButton }
            busy = true
            scope.launch {
                error = withContext(Dispatchers.Default) { pm.create(name, phone, pin, bio) }
                busy = false
                if (error == null) onDone()
            }
        }
        if (canCancel) Text("Cancel", color = P.Violet, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onCancel).padding(12.dp))
    }
}

@Composable
private fun PinEntry(pm: ProfileManager, p: Profile, onBack: () -> Unit, onOk: (Profile) -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun bio() = biometric(ctx as FragmentActivity, p.name) { onOk(p) }
    LaunchedEffect(p) { if (p.biometric && canBiometric(ctx)) bio() }

    SCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(p.name, 64.dp)
            Spacer(Modifier.height(8.dp))
            Text("Hi ${p.name} 👋", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(ProfileManager.mask(p.phone), color = P.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) P.Violet else P.Line))
                }
            }
            Text(error ?: " ", color = P.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            NumPad(decimal = false, onKey = { k ->
                if (System.currentTimeMillis() < lockedUntil) { error = "Too many tries. Wait 30 s."; return@NumPad }
                pin = if (k == "⌫") pin.dropLast(1) else (pin + k).take(4)
                if (pin.length == 4) scope.launch {
                    val ok = withContext(Dispatchers.Default) { pm.verify(p, pin) }
                    if (ok) onOk(p) else {
                        fails++; pin = ""
                        if (fails >= 5) { lockedUntil = System.currentTimeMillis() + 30_000; fails = 0 }
                        error = "Wrong PIN"
                        for (x in listOf(24f, -24f, 16f, -16f, 0f)) { shake.animateTo(x, spring(stiffness = 4000f)) }
                        delay(10)
                    }
                }
            })
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("‹ Profiles", color = P.Violet, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
                if (p.biometric && canBiometric(ctx)) Text("Use fingerprint", color = P.Violet, modifier = Modifier.clickable { bio() }.padding(10.dp))
            }
        }
    }
}

fun canBiometric(ctx: android.content.Context) =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun biometric(act: FragmentActivity, name: String, onOk: () -> Unit) {
    val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle("Unlock SpliWise").setSubtitle(name)
            .setNegativeButtonText("Use PIN").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
    )
}

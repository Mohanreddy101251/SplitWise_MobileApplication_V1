package com.spliwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spliwise.classify.Categories
import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.data.Category
import com.spliwise.data.Repo
import com.spliwise.data.Txn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/** Bank payments the app couldn't place: add a note, tap a category (remembered for that shop). */
@Composable
fun ReviewScreen(repo: Repo, cats: Map<String, Category>, onBack: () -> Unit) {
    val list by repo.review.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            item {
                Header(P.Sunset) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center) { Text("‹", color = Color.White, fontSize = 24.sp) }
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text("Needs a category", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${list.size} left · add a note, tap a category", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                }
            }
            if (list.isEmpty()) item { Empty("🎉", "All sorted!") }
            items(list, key = { it.id }) { t ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { ReviewCard(t, cats, repo, scope) }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }

        // Jump to top / bottom
        if (list.size > 2) Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            JumpButton("▲") { scope.launch { state.animateScrollToItem(0) } }
            JumpButton("▼") { scope.launch { state.animateScrollToItem(list.size + 1) } }
        }
    }
}

@Composable
private fun JumpButton(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).shadow(10.dp, CircleShape, spotColor = P.Pink).clip(CircleShape).background(P.Sunset).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(t: Txn, cats: Map<String, Category>, repo: Repo, scope: kotlinx.coroutines.CoroutineScope) {
    val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    var note by remember(t.id) { mutableStateOf(t.note) }
    val pickable = cats.values.filter { it.id != Categories.UNKNOWN }
    SCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${java.time.Instant.ofEpochMilli(t.ts).atZone(Dates.zone).format(fmt)} · ${t.bank?.uppercase() ?: ""}" +
                        (t.account?.let { " ••$it" } ?: ""),
                    color = P.Sub, fontSize = 12.sp,
                )
            }
            Text((if (t.isCredit) "+" else "−") + Money.format(t.amount), fontWeight = FontWeight.ExtraBold,
                color = if (t.isCredit) P.Green else P.Ink, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                note, { note = it.take(80) }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Note — e.g. lunch with team at office", fontSize = 13.sp) },
                shape = RoundedCornerShape(14.dp),
            )
            if (note != t.note) Text("Save", color = P.Violet, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).clickable { scope.launch { repo.update(t.copy(note = note.trim())) } }.padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val first = if (t.category != Categories.UNKNOWN) listOf(cats.of(t.category)) else emptyList()
            (first + pickable.filter { it.id != t.category }).forEach { c ->
                Chip("${c.emoji} ${c.name}", c.id == t.category && t.category != Categories.UNKNOWN, c.c()) {
                    scope.launch { repo.setCategory(t.copy(note = note.trim()), c.id) } // note is kept with the category
                }
            }
        }
    }
}

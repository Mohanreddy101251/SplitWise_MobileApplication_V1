package com.spliwise.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spliwise.MainActivity
import com.spliwise.R
import com.spliwise.classify.Categories
import com.spliwise.container
import com.spliwise.core.Money
import com.spliwise.data.Txn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Notifier {
    private const val REVIEW = "review"
    private const val SUMMARY = "summary"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(REVIEW, "Needs a category", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_LOW))
    }

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, review: Boolean) = PendingIntent.getActivity(
        ctx, if (review) 2 else 3,
        Intent(ctx, MainActivity::class.java).putExtra("open", if (review) "review" else "home")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "₹250 at XYZ — what was it?" with up to 3 one-tap category buttons. */
    fun unknown(ctx: Context, t: Txn, guesses: List<String>) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, REVIEW)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Money.format(t.amount)} ${if (t.isCredit) "from" else "at"} ${t.merchant}")
            .setContentText("What was this? Tap a category — I'll remember it.")
            .setContentIntent(openApp(ctx, true))
            .setAutoCancel(true)
        guesses.take(3).forEachIndexed { i, cat ->
            val def = Categories.defaults.firstOrNull { it.id == cat } ?: return@forEachIndexed
            val pi = PendingIntent.getBroadcast(
                ctx, (t.id * 4 + i).toInt(),
                Intent(ctx, CategorizeReceiver::class.java).putExtra("id", t.id).putExtra("cat", cat),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "${def.emoji} ${def.name}", pi)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(t.id.toInt(), b.build()) }
    }

    /** Status pop-ups clear themselves after 5 seconds (as requested); the app keeps the full status. */
    private const val SHORT_MS = 5_000L

    fun summary(ctx: Context, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, SUMMARY)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("SpliWise · today").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setTimeoutAfter(SHORT_MS)
            .setContentIntent(openApp(ctx, false)).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, n) }
    }
}

/** Handles the category buttons on an "unknown transaction" notification. */
class CategorizeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val cat = intent.getStringExtra("cat") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ctx.container.activeRepo() ?: return@launch
                repo.get(id)?.let { repo.setCategory(it, cat) }
                NotificationManagerCompat.from(ctx).cancel(id.toInt())
            } finally { pending.finish() }
        }
    }
}

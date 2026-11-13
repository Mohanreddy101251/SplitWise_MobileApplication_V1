package com.spliwise.ingest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spliwise.container
import com.spliwise.core.Money
import com.spliwise.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Live bank alerts: parsed the moment the SMS arrives. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ctx.container.activeRepo() ?: return@launch
                val s = ctx.container.activeSettings() ?: return@launch
                val items = msgs.groupBy { it.originatingAddress.orEmpty() }.flatMap { (from, parts) ->
                    Importer.parseSms(from, parts.joinToString("") { it.messageBody.orEmpty() }, parts.first().timestampMillis, s)
                }
                val r = repo.ingest(items)
                val cz = repo.categorizer()
                r.needReview.forEach { Notifier.unknown(ctx, it, cz.suggestions(it.merchant, it.isCredit)) }
            } finally { pending.finish() }
        }
    }
}

object AlarmScheduler {
    private const val HOUR = 23
    private const val MIN = 50

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        var at = now.with(LocalTime.of(HOUR, MIN)).withSecond(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val t = at.toInstant().toEpochMilli()
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }

    /** Sends the bank mini-statement SMS requests now, then reads the replies [collectAfterMin] minutes later. */
    fun runNow(ctx: Context, sendRequests: Boolean, collectAfterMin: Long = 15) {
        val wm = WorkManager.getInstance(ctx)
        if (sendRequests) wm.enqueueUniqueWork(
            "nightly-request", androidx.work.ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RequestWorker>().build(),
        )
        wm.enqueueUniqueWork(
            "nightly-collect", androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CollectWorker>()
                .setInitialDelay(if (sendRequests) collectAfterMin else 0, TimeUnit.MINUTES)
                .setInputData(workDataOf("since" to System.currentTimeMillis() - 24 * 3_600_000L)).build(),
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.runNow(ctx, sendRequests = true)
        AlarmScheduler.scheduleNext(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AlarmScheduler.scheduleNext(ctx)
    }
}

class RequestWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val s = applicationContext.container.activeSettings() ?: return Result.success()
        s.lastRun = "Requests: " + Importer.sendRequests(applicationContext, s).joinToString(" · ")
        return Result.success()
    }
}

/** Every hour: read new bank SMS since the last check. */
class SmsSyncWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val repo = c.activeRepo() ?: return Result.success()
        val s = c.activeSettings() ?: return Result.success()
        val profile = c.profiles.active() ?: return Result.success()

        // Daily encrypted backup to Downloads/SpliWise (when it's more than 20 h old).
        val b = c.backup(profile.phone)
        if (com.spliwise.backup.Backups.due(b)) runCatching {
            com.spliwise.backup.Backups.run(applicationContext, repo, profile.phone, profile.name, s, b)
        }.onFailure { b.lastResult = "Backup failed: ${it.message}" }

        if (s.enabledBanks.isEmpty()) return Result.success()
        val now = System.currentTimeMillis()
        val since = (s.lastSmsScan.takeIf { it > 0 } ?: (now - 2 * 3_600_000L)) - 5 * 60_000L // small overlap; duplicates merge
        val r = runCatching { Importer.fromInbox(applicationContext, repo, s, since) }.getOrNull() ?: return Result.retry()
        s.lastSmsScan = now
        val cz = repo.categorizer()
        r.needReview.forEach { Notifier.unknown(applicationContext, it, cz.suggestions(it.merchant, it.isCredit)) }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "sms-hourly", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<SmsSyncWorker>(1, TimeUnit.HOURS).build(),
        )

        /** Run one catch-up now (e.g. right after a restore). */
        fun runOnce(ctx: Context) = WorkManager.getInstance(ctx).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
        )
    }
}

/** After the 11:50 PM request: reads the last 24 h of bank SMS (incl. mini-statement replies), de-duplicates, sends a summary. */
class CollectWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val repo = c.activeRepo() ?: return Result.success()
        val s = c.activeSettings() ?: return Result.success()
        val since = inputData.getLong("since", System.currentTimeMillis() - 24 * 3_600_000L)
        val sms = Importer.fromInbox(applicationContext, repo, s, since) // re-reading is safe: duplicates merge
        val cz = repo.categorizer()
        sms.needReview.forEach { Notifier.unknown(applicationContext, it, cz.suggestions(it.merchant, it.isCredit)) }

        val dayStart = com.spliwise.core.Dates.dayStart(LocalDate.now())
        val today = repo.db.txn().rangeOnce(dayStart, System.currentTimeMillis())
        val spent = com.spliwise.core.Stats.spent(today)
        val pending = today.count { it.needsReview }
        s.lastRun = "${ZonedDateTime.now().toLocalDateTime().toString().take(16).replace('T', ' ')} · SMS +${sms.added}" +
            if (sms.merged > 0) " (${sms.merged} already there)" else ""
        Notifier.summary(
            applicationContext,
            "Today you spent ${Money.format(spent)} across ${today.count { !it.isCredit }} payments" +
                if (pending > 0) " · $pending need a category" else "",
        )
        return Result.success()
    }
}

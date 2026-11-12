package com.spliwise

import android.app.Application
import android.content.Context
import com.spliwise.auth.ProfileManager
import com.spliwise.data.Repo
import com.spliwise.data.SpliDb
import com.spliwise.ingest.AlarmScheduler
import com.spliwise.ingest.BankSettings
import com.spliwise.notify.Notifier

class App : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        AlarmScheduler.scheduleNext(this)          // 11:50 PM: bank requests + Gmail (once a day)
        com.spliwise.ingest.SmsSyncWorker.schedule(this) // every hour: new bank SMS only
    }
}

/** Tiny manual DI: one encrypted DB open at a time (the active profile's). */
class AppContainer(private val app: Application) {
    val profiles = ProfileManager(app)
    private var open: Pair<String, Repo>? = null

    @Synchronized
    fun repoFor(phone: String): Repo {
        open?.let { if (it.first == phone) return it.second }
        open?.second?.db?.close()
        val repo = Repo(SpliDb.open(app, ProfileManager.dbName(phone), profiles.dbKey(phone)))
        open = phone to repo
        return repo
    }

    /** Background jobs (SMS, nightly import) write into the last unlocked profile. */
    fun activeRepo(): Repo? = profiles.active()?.let { repoFor(it.phone) }
    fun settings(phone: String) = BankSettings(app, phone)
    fun activeSettings(): BankSettings? = profiles.active()?.let { settings(it.phone) }
}

val Context.container: AppContainer get() = (applicationContext as App).container

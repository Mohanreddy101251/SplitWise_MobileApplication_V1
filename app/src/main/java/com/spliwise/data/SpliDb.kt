package com.spliwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spliwise.classify.Categories
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Txn::class, Category::class, Rule::class, Person::class, Split::class, Share::class, Settlement::class, Ignored::class],
    version = 2,
    exportSchema = false,
)
abstract class SpliDb : RoomDatabase() {
    abstract fun txn(): TxnDao
    abstract fun cat(): CategoryDao
    abstract fun rule(): RuleDao
    abstract fun people(): PersonDao
    abstract fun split(): SplitDao
    abstract fun ignored(): IgnoredDao

    companion object {
        /** v1 -> v2: credit-card flag on transactions + ignored statement rows. Keeps all existing data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txn ADD COLUMN card INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS ignored (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        private val nativeLoaded by lazy { System.loadLibrary("sqlcipher") }

        /** Opens one profile's AES-256 encrypted database (SQLCipher). */
        fun open(ctx: Context, name: String, key: ByteArray): SpliDb =
            Room.databaseBuilder(ctx.applicationContext, SpliDb::class.java, name.also { nativeLoaded })
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Categories.defaults.forEachIndexed { i, c ->
                            db.execSQL(
                                "INSERT INTO category(id, name, emoji, color, budget, sort) VALUES (?, ?, ?, ?, 0, ?)",
                                arrayOf<Any>(c.id, c.name, c.emoji, c.color, i),
                            )
                        }
                    }
                })
                .build()
    }
}

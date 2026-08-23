package com.mcserverstatus.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ServerEntry::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: `port` became nullable (null = auto-detect). SQLite can't ALTER a NOT NULL
         * constraint away, so rebuild the table, preserving every existing row's port as-is. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE servers_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "host TEXT NOT NULL, " +
                        "port INTEGER, " +
                        "edition TEXT NOT NULL" +
                        ")",
                )
                db.execSQL(
                    "INSERT INTO servers_new (id, name, host, port, edition) " +
                        "SELECT id, name, host, port, edition FROM servers",
                )
                db.execSQL("DROP TABLE servers")
                db.execSQL("ALTER TABLE servers_new RENAME TO servers")
            }
        }

        /** v2 -> v3: adds the two columns backing per-server status-change notifications. Plain
         * additive columns, so a simple ALTER TABLE suffices (no table rebuild needed here). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN notifyOnStatusChange INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastKnownOnline INTEGER DEFAULT NULL")
            }
        }

        /** v3 -> v4: adds the widget's "favorite server" flag. Another plain additive column. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mc-server-status.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
        }
    }
}

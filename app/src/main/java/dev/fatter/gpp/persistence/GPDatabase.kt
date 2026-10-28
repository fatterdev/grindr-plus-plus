package dev.fatter.gpp.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.fatter.gpp.persistence.converters.DateConverter
import dev.fatter.gpp.persistence.dao.AlbumDao
import dev.fatter.gpp.persistence.dao.BlockEventDao
import dev.fatter.gpp.persistence.dao.SavedPhraseDao
import dev.fatter.gpp.persistence.dao.TeleportLocationDao
import dev.fatter.gpp.persistence.model.AlbumContentEntity
import dev.fatter.gpp.persistence.model.AlbumEntity
import dev.fatter.gpp.persistence.model.BlockEventEntity
import dev.fatter.gpp.persistence.model.SavedPhraseEntity
import dev.fatter.gpp.persistence.model.TeleportLocationEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        BlockEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun blockEventDao(): BlockEventDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `block_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`profileId` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`eventType` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`packageName` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_block_events_timestamp` ON `block_events` (`timestamp`)"
                )
            }
        }

        fun create(context: Context): GPDatabase {
            return Room.databaseBuilder(context, GPDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(false)
                .addMigrations(MIGRATION_5_6)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}

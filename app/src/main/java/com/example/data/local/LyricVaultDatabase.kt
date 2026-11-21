package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        IdeaNoteEntity::class,
        IdeaBlockEntity::class,
        SongEntity::class,
        IdeaUsageEntity::class,
        IdeaEmbeddingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LyricVaultDatabase : RoomDatabase() {
    abstract fun lyricVaultDao(): LyricVaultDao

    companion object {
        @Volatile
        private var INSTANCE: LyricVaultDatabase? = null

        /**
         * v1 -> v2: adds the idea_embeddings table (vector store). No existing
         * table changes, so no data is touched. The CREATE TABLE must match
         * Room's expected schema for IdeaEmbeddingEntity exactly.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `idea_embeddings` (" +
                        "`ideaId` INTEGER NOT NULL, " +
                        "`embedding` BLOB NOT NULL, " +
                        "`engineId` TEXT NOT NULL, " +
                        "`dimensions` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ideaId`), " +
                        "FOREIGN KEY(`ideaId`) REFERENCES `idea_notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_idea_embeddings_engineId` " +
                        "ON `idea_embeddings` (`engineId`)"
                )
            }
        }

        fun getInstance(context: Context): LyricVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LyricVaultDatabase::class.java,
                    "lyric_vault.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}

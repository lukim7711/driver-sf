package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.data.model.FlowBoardItem

/**
 * Room Database untuk app PoC Screen Reader.
 *
 * v1: captures table
 * v2: + tag, note, is_starred columns
 * v3: + flow_boards, flow_board_items tables
 * v4: + content_hash column + index untuk dedup lintas sesi
 *
 * Schema export di-enable agar Room generate JSON schema file
 * di build time. File ini berguna untuk:
 * - Validasi migration: Room bisa compare schema lama vs baru
 * - Dokumentasi: developer bisa lihat struktur DB tanpa baca kode
 * - Testing: AutoMigrationSpec bisa digunakan di masa depan
 *
 * Output lokasi: app/schemas/com.driversfpoc.screenreader.data.AppDatabase/
 */
@Database(
    entities = [CaptureRecord::class, FlowBoard::class, FlowBoardItem::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun captureDao(): CaptureDao
    abstract fun flowBoardDao(): FlowBoardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration v1 → v2: tag, note, is_starred */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE captures ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE captures ADD COLUMN is_starred INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Migration v2 → v3: flow_boards + flow_board_items tables */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS flow_boards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL,
                        item_count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS flow_board_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        flow_board_id INTEGER NOT NULL,
                        capture_id INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (flow_board_id) REFERENCES flow_boards(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_flow_board_items_flow_board_id ON flow_board_items(flow_board_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_flow_board_items_capture_id ON flow_board_items(capture_id)")
            }
        }

        /**
         * Migration v3 → v4: content_hash untuk dedup lintas sesi.
         *
         * Menambahkan kolom content_hash (INTEGER) ke tabel captures.
         * Default 0 untuk record lama (sebelum v4) yang belum punya hash.
         * Index pada content_hash untuk O(1) lookup saat dedup check.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE captures ADD COLUMN content_hash INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_captures_content_hash ON captures(content_hash)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_reader_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

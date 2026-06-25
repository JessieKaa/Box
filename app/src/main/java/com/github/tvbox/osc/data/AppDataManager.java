package com.github.tvbox.osc.data;

import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.io.IOException;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class AppDataManager {
    private static final int DB_FILE_VERSION = 5;
    private static final String DB_NAME = "tvbox";

    /**
     * Must match the Room identity hash generated from {@link AppDataBase} at compile time.
     * If the on-disk DB stores a different hash, it is from an older incompatible build and
     * gets discarded by {@link #dropStaleSchemaFile()}.
     */
    private static final String EXPECTED_DB_IDENTITY_HASH = "c0cfc4e577af25a1fda401263924b8fa";
    private static volatile AppDataManager manager;
    private static AppDataBase dbInstance;

    private AppDataManager() {
    }

    public static void init() {
        if (manager == null) {
            synchronized (AppDataManager.class) {
                if (manager == null) {
                    manager = new AppDataManager();
                }
            }
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                //database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT");
                database.execSQL("CREATE TABLE IF NOT EXISTS `storageDrive` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `type` INTEGER NOT NULL, `configJson` TEXT)");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS t_search (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, searchKeyWords TEXT)");
                database.execSQL("CREATE INDEX IF NOT EXISTS index_t_search_searchKeyWords ON t_search (searchKeyWords)");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `karaokeHistory` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT, " +
                        "`title` TEXT, " +
                        "`artist` TEXT, " +
                        "`displayName` TEXT, " +
                        "`fileSize` INTEGER NOT NULL, " +
                        "`lastModified` INTEGER NOT NULL, " +
                        "`duration` INTEGER NOT NULL, " +
                        "`playedAt` INTEGER NOT NULL, " +
                        "`playbackPosition` INTEGER NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_karaokeHistory_filePath ON `karaokeHistory` (`filePath`)");

                database.execSQL("CREATE TABLE IF NOT EXISTS `karaokeFavorite` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT, " +
                        "`title` TEXT, " +
                        "`artist` TEXT, " +
                        "`displayName` TEXT, " +
                        "`fileSize` INTEGER NOT NULL, " +
                        "`lastModified` INTEGER NOT NULL, " +
                        "`duration` INTEGER NOT NULL, " +
                        "`favoritedAt` INTEGER NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_karaokeFavorite_filePath ON `karaokeFavorite` (`filePath`)");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE karaokeHistory ADD COLUMN identityKey TEXT");
            database.execSQL("ALTER TABLE karaokeHistory ADD COLUMN trackId TEXT");
            database.execSQL("ALTER TABLE karaokeHistory ADD COLUMN sourceType TEXT");
            database.execSQL("ALTER TABLE karaokeHistory ADD COLUMN streamUrl TEXT");
            database.execSQL("ALTER TABLE karaokeHistory ADD COLUMN artworkUrl TEXT");
            database.execSQL("ALTER TABLE karaokeFavorite ADD COLUMN identityKey TEXT");
            database.execSQL("ALTER TABLE karaokeFavorite ADD COLUMN trackId TEXT");
            database.execSQL("ALTER TABLE karaokeFavorite ADD COLUMN sourceType TEXT");
            database.execSQL("ALTER TABLE karaokeFavorite ADD COLUMN streamUrl TEXT");
            database.execSQL("ALTER TABLE karaokeFavorite ADD COLUMN artworkUrl TEXT");

            database.execSQL("DELETE FROM karaokeHistory WHERE filePath IS NULL OR filePath = ''");
            database.execSQL("DELETE FROM karaokeFavorite WHERE filePath IS NULL OR filePath = ''");
            database.execSQL("UPDATE karaokeHistory SET sourceType='local', identityKey='local:' || filePath WHERE identityKey IS NULL");
            database.execSQL("UPDATE karaokeFavorite SET sourceType='local', identityKey='local:' || filePath WHERE identityKey IS NULL");

            database.execSQL("DROP INDEX IF EXISTS index_karaokeHistory_filePath");
            database.execSQL("DROP INDEX IF EXISTS index_karaokeFavorite_filePath");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_karaokeHistory_identityKey ON karaokeHistory(identityKey)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_karaokeFavorite_identityKey ON karaokeFavorite(identityKey)");
        }
    };

    static String dbPath() {
        return DB_NAME + ".v" + DB_FILE_VERSION + ".db";
    }

    /**
     * Before opening Room, rename the previous-version DB file to the current version's name
     * so that Room's {@link #MIGRATION_3_4} can run on it. The DB filename embeds the version
     * number, so Room would otherwise treat a missing v4 file as a fresh install and skip
     * migrations entirely.
     */
    private static void migrateDbFileAcrossVersions() {
        File dbDir = App.getInstance().getDatabasePath(dbPath()).getParentFile();
        if (dbDir == null) return;
        File target = new File(dbDir, dbPath());
        if (target.exists()) return;

        File previous = new File(dbDir, DB_NAME + ".v" + (DB_FILE_VERSION - 1) + ".db");
        if (previous.exists()) {
            //noinspection ResultOfMethodCallIgnored
            previous.renameTo(target);
        }
    }

    /**
     * Room stores an identity hash in sqlite_master that reflects the entity schema compiled
     * into the app. If a previous build's DB file is still on disk (e.g. a v5 file written by
     * an earlier release whose entities differed from the current v5), Room refuses to open it
     * with "Room cannot verify the data integrity", which breaks every DAO call. Detect that
     * case by probing the stored hash and delete the stale file so Room can recreate it.
     */
    private static void dropStaleSchemaFile() {
        try {
            File dbDir = App.getInstance().getDatabasePath(dbPath()).getParentFile();
            if (dbDir == null) return;
            File target = new File(dbDir, dbPath());
            if (!target.exists()) return;

            android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    target.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            String hash = null;
            try {
                android.database.Cursor c = db.rawQuery(
                        "SELECT identity_hash FROM room_master_table WHERE id = 0", null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            hash = c.getString(0);
                        }
                    } finally {
                        c.close();
                    }
                }
            } finally {
                db.close();
            }

            // Hash written by the current build (see AppDataBase). If the on-disk hash differs,
            // the file is from an incompatible older build and must be discarded.
            String expected = EXPECTED_DB_IDENTITY_HASH;
            if (!expected.equals(hash)) {
                android.util.Log.w("AppDataManager", "Discarding stale DB file: stored hash=" + hash + " expected=" + expected);
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                File journal = new File(dbDir, dbPath() + "-journal");
                if (journal.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    journal.delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        if (dbInstance == null) {
            migrateDbFileAcrossVersions();
            dropStaleSchemaFile();
            dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
                        }
                    }).allowMainThreadQueries()
                    .build();
        }
        return dbInstance;
    }

    public static boolean backup(File path) throws IOException {
        if (dbInstance != null && dbInstance.isOpen()) {
            dbInstance.close();
        }
        File db = App.getInstance().getDatabasePath(dbPath());
        if (db.exists()) {
            FileUtils.copyFile(db, path);
            return true;
        } else {
            return false;
        }
    }

    public static boolean restore(File path) throws IOException {
        if (dbInstance != null && dbInstance.isOpen()) {
            dbInstance.close();
        }
        File db = App.getInstance().getDatabasePath(dbPath());
        if (db.exists()) {
            db.delete();
        }
        if (!db.getParentFile().exists())
            db.getParentFile().mkdirs();
        FileUtils.copyFile(path, db);
        return true;
    }
}

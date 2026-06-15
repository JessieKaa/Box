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
    private static final int DB_FILE_VERSION = 4;
    private static final String DB_NAME = "tvbox";
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

    public static AppDataBase get() {
        if (manager == null) {
            throw new RuntimeException("AppDataManager is no init");
        }
        if (dbInstance == null) {
            migrateDbFileAcrossVersions();
            dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase.class, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
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

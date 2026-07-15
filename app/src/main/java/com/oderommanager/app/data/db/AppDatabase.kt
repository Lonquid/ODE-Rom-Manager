package com.oderommanager.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.oderommanager.app.data.model.BackupLogEntry
import com.oderommanager.app.data.model.BackupStatus
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType

class Converters {
    @TypeConverter
    fun fromSystemType(value: SystemType): String = value.name

    @TypeConverter
    fun toSystemType(value: String): SystemType = SystemType.valueOf(value)

    @TypeConverter
    fun fromBackupStatus(value: BackupStatus): String = value.name

    @TypeConverter
    fun toBackupStatus(value: String): BackupStatus = BackupStatus.valueOf(value)
}

@Database(
    entities = [RomEntry::class, BackupLogEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun romEntryDao(): RomEntryDao
    abstract fun backupLogDao(): BackupLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ode_rom_manager.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

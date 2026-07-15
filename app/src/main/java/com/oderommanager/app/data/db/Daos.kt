package com.oderommanager.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.oderommanager.app.data.model.BackupLogEntry
import com.oderommanager.app.data.model.BackupStatus
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType

@Dao
interface RomEntryDao {

    @Query("SELECT * FROM rom_entries ORDER BY displayName ASC")
    fun getAllRoms(): LiveData<List<RomEntry>>

    @Query("SELECT * FROM rom_entries WHERE systemType = :systemType ORDER BY displayName ASC")
    fun getRomsBySystem(systemType: SystemType): LiveData<List<RomEntry>>

    @Query("SELECT * FROM rom_entries WHERE isRomHack = 1 ORDER BY displayName ASC")
    fun getRomHacks(): LiveData<List<RomEntry>>

    @Query("SELECT * FROM rom_entries WHERE hasArtwork = 0 AND systemType = 'GBA' ORDER BY displayName ASC")
    fun getGbaRomsMissingArtwork(): LiveData<List<RomEntry>>

    @Query("SELECT * FROM rom_entries WHERE md5Hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): RomEntry?

    @Query("SELECT * FROM rom_entries WHERE assignedGameCode = :code LIMIT 1")
    suspend fun findByAssignedCode(code: String): RomEntry?

    @Query("SELECT * FROM rom_entries WHERE id = :id")
    suspend fun getById(id: Long): RomEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rom: RomEntry): Long

    @Update
    suspend fun update(rom: RomEntry)

    @Delete
    suspend fun delete(rom: RomEntry)

    @Query("DELETE FROM rom_entries WHERE sdCardPath = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM rom_entries")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM rom_entries WHERE hasArtwork = 1")
    suspend fun getArtworkCount(): Int

    @Query("SELECT COUNT(*) FROM rom_entries WHERE isRomHack = 1")
    suspend fun getHackCount(): Int
}

@Dao
interface BackupLogDao {

    @Query("SELECT * FROM backup_log ORDER BY dateModified DESC")
    fun getAllEntries(): LiveData<List<BackupLogEntry>>

    @Query("SELECT * FROM backup_log WHERE status = 'PENDING' ORDER BY dateModified DESC")
    fun getPendingEntries(): LiveData<List<BackupLogEntry>>

    @Query("SELECT * FROM backup_log WHERE id = :id")
    suspend fun getById(id: Long): BackupLogEntry?

    @Query("SELECT * FROM backup_log WHERE newGameCode = :code LIMIT 1")
    suspend fun findByGameCode(code: String): BackupLogEntry?

    @Insert
    suspend fun insert(entry: BackupLogEntry): Long

    @Update
    suspend fun update(entry: BackupLogEntry)

    @Query("UPDATE backup_log SET status = :status, dateConfirmed = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: BackupStatus, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM backup_log WHERE status = 'PENDING'")
    fun getPendingCount(): LiveData<Int>
}

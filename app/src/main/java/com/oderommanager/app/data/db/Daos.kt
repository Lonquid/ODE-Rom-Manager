package com.oderommanager.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.oderommanager.app.data.model.BackupLogEntry
import com.oderommanager.app.data.model.BackupStatus
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType

@Dao
interface RomEntryDao {

    @Query("SELECT * FROM rom_entries ORDER BY folderName ASC, displayName ASC")
    fun getAllRoms(): LiveData<List<RomEntry>>

    @Query("SELECT DISTINCT folderName FROM rom_entries ORDER BY folderName ASC")
    fun getAllFolderNames(): LiveData<List<String>>

    @Query("SELECT * FROM rom_entries WHERE folderName = :folder ORDER BY displayName ASC")
    fun getRomsByFolder(folder: String): LiveData<List<RomEntry>>

    @Query("SELECT * FROM rom_entries WHERE systemType = :systemType ORDER BY displayName ASC")
    fun getRomsBySystem(systemType: SystemType): LiveData<List<RomEntry>>

    @Query("""SELECT * FROM rom_entries WHERE systemType = 'GBA'
              AND (headerMismatch = 1 OR assignedGameCode IS NOT NULL)
              ORDER BY displayName ASC""")
    fun getHackCandidates(): LiveData<List<RomEntry>>

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

    @Query("UPDATE rom_entries SET artVerified = :verified, dateModified = :ts WHERE id = :id")
    suspend fun setArtVerified(id: Long, verified: Boolean, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM rom_entries")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM rom_entries WHERE hasArtwork = 1")
    suspend fun getArtworkCount(): Int

    @Query("SELECT COUNT(*) FROM rom_entries WHERE headerMismatch = 1")
    suspend fun getHackCount(): Int
}

@Dao
interface BackupLogDao {

    @Query("SELECT * FROM backup_log ORDER BY dateModified DESC")
    fun getAllEntries(): LiveData<List<BackupLogEntry>>

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

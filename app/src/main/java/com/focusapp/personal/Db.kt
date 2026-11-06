package com.focusapp.personal

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// SRD section 5.1
@Entity(tableName = "app_directory")
data class AppEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val category: String // Productive, Distracting, Neutral
)

// SRD section 5.2
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val isStrictMode: Boolean,
    val status: String, // Active, Completed, Failed
    val tag: String
)

// SRD section 5.3
@Entity(tableName = "usage_logs")
data class UsageLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val durationSeconds: Int
)

// SRD section 5.4
@Entity(tableName = "blacklist_rules")
data class BlacklistRule(
    @PrimaryKey(autoGenerate = true) val ruleId: Long = 0,
    val packageName: String,
    val blockType: String // FullApp, ReelsOnly, ShortsOnly
)

// Weekly schedule: one row per day-of-week and time window (day 1 = Monday ... 7 = Sunday).
@Entity(tableName = "schedule_slots")
data class ScheduleSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val startMin: Int, // minutes after midnight
    val endMin: Int,
    val tag: String
)

@Dao
interface FocusDao {
    @Insert
    fun insertSession(session: FocusSession): Long

    @Query("UPDATE focus_sessions SET status = :status, endTime = :end WHERE sessionId = :id")
    fun finishSession(id: Long, status: String, end: Long)

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :from")
    fun sessionsSince(from: Long): List<FocusSession>

    @Query("SELECT * FROM focus_sessions WHERE status = 'Completed'")
    fun completedSessions(): List<FocusSession>

    @Insert
    fun insertSlot(slot: ScheduleSlot): Long

    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek, startMin")
    fun slots(): List<ScheduleSlot>

    @Query("DELETE FROM schedule_slots WHERE id = :id")
    fun deleteSlot(id: Long)

    @Insert
    fun insertLog(log: UsageLog)

    @Query("DELETE FROM blacklist_rules")
    fun clearRules()

    @Insert
    fun insertRules(rules: List<BlacklistRule>)

    @Query("SELECT * FROM blacklist_rules")
    fun rules(): List<BlacklistRule>

    @Query("DELETE FROM app_directory")
    fun clearApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApps(apps: List<AppEntry>)
}

@Database(
    entities = [AppEntry::class, FocusSession::class, UsageLog::class, BlacklistRule::class, ScheduleSlot::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FocusDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDb::class.java, "focus.db"
                )
                    // Small personal app: simple synchronous queries are fine.
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

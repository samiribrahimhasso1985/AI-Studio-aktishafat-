package com.iktishafat.teleprompter.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.iktishafat.teleprompter.domain.model.Script
import com.iktishafat.teleprompter.domain.model.ScriptLanguage
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scripts", indices = [Index("updatedAt")])
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val language: String,
    val folder: String,
    val pinned: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "0") val lastReadOffset: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

fun ScriptEntity.toDomain() = Script(
    id = id, title = title, body = body, language = ScriptLanguage.fromName(language),
    folder = folder, pinned = pinned, lastReadOffset = lastReadOffset, createdAt = createdAt, updatedAt = updatedAt,
)

fun Script.toEntity() = ScriptEntity(
    id = id, title = title, body = body, language = language.name,
    folder = folder, pinned = pinned, lastReadOffset = lastReadOffset, createdAt = createdAt, updatedAt = updatedAt,
)

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query(
        "SELECT * FROM scripts WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' " +
            "OR folder LIKE '%' || :q || '%' ORDER BY pinned DESC, updatedAt DESC"
    )
    fun search(q: String): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun observeById(id: Long): Flow<ScriptEntity?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: Long): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScriptEntity): Long

    @Update
    suspend fun update(entity: ScriptEntity): Int

    @Query("UPDATE scripts SET lastReadOffset = :offset WHERE id = :id")
    suspend fun setLastReadOffset(id: Long, offset: Int)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ScriptEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iktishafat_teleprompter.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}

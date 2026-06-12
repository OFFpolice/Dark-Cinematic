package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallpapers")
data class WallpaperItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val videoUriStr: String, // Keep string URI or file path
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L, // 0L means untrimmed (play till end)
    val isLooping: Boolean = true,
    val thumbnailUriStr: String? = null,
    val nightModeEnabled: Boolean = false,
    val nightBrightnessReduction: Float = 0.4f, // 0.0 = no dimming, 0.8 = 80% dimming
    val isActive: Boolean = false
)

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY id DESC")
    fun getAllWallpapers(): Flow<List<WallpaperItem>>

    @Query("SELECT * FROM wallpapers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveWallpaper(): WallpaperItem?

    @Query("SELECT * FROM wallpapers WHERE isActive = 1")
    fun getActiveWallpaperFlow(): Flow<WallpaperItem?>

    @Query("SELECT * FROM wallpapers WHERE id = :id")
    suspend fun getWallpaperById(id: Int): WallpaperItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: WallpaperItem): Long

    @Update
    suspend fun updateWallpaper(wallpaper: WallpaperItem)

    @Delete
    suspend fun deleteWallpaper(wallpaper: WallpaperItem)

    @Query("UPDATE wallpapers SET isActive = 0")
    suspend fun clearAllActive()

    @Transaction
    suspend fun setActiveWallpaper(id: Int) {
        clearAllActive()
        val item = getWallpaperById(id)
        if (item != null) {
            updateWallpaper(item.copy(isActive = true))
        }
    }
}

@Database(entities = [WallpaperItem::class], version = 1, exportSchema = false)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract val wallpaperDao: WallpaperDao
}

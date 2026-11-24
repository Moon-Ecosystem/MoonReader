package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryOrderDao {
    @Query("SELECT * FROM library_custom_order")
    fun observe(): Flow<List<LibraryOrderEntity>>

    @Query("DELETE FROM library_custom_order")
    suspend fun clear()

    @Insert
    suspend fun insert(rows: List<LibraryOrderEntity>)

    @Transaction
    suspend fun replace(ids: List<String>) {
        clear()
        insert(ids.mapIndexed { index, id -> LibraryOrderEntity(id, index) })
    }
}

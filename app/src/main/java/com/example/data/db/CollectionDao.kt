package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY position, id")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_manga")
    fun observeMembers(): Flow<List<CollectionMangaEntity>>

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("SELECT COALESCE(MAX(position), -1) FROM collections")
    suspend fun lastPosition(): Int

    /** Adds a collection after the existing ones and returns its id. */
    @Transaction
    suspend fun create(name: String): Long = insert(CollectionEntity(name = name, position = lastPosition() + 1))

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collection_manga WHERE collectionId = :id")
    suspend fun deleteMembers(id: Long)

    /** Removes the collection and its memberships; the series themselves stay in the library. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteMembers(id)
        deleteCollection(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(member: CollectionMangaEntity)

    @Query("DELETE FROM collection_manga WHERE collectionId = :collectionId AND mangaId = :mangaId")
    suspend fun removeMember(collectionId: Long, mangaId: String)
}

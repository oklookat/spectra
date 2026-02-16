package com.oklookat.spectra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oklookat.spectra.model.Resource
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources")
    fun getAllResourcesFlow(): Flow<List<Resource>>

    @Query("SELECT * FROM resources")
    suspend fun getAllResources(): List<Resource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: Resource)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<Resource>)

    @Query("DELETE FROM resources WHERE name = :name")
    suspend fun deleteResource(name: String)
}

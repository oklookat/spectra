package com.oklookat.spectra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM groups")
    fun getAllGroupsFlow(): Flow<List<Group>>

    @Query("SELECT * FROM groups")
    suspend fun getAllGroups(): List<Group>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<Group>)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM profiles")
    fun getAllProfilesFlow(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles")
    suspend fun getAllProfiles(): List<Profile>

    @Query("SELECT * FROM profiles WHERE groupId = :groupId")
    suspend fun getProfilesByGroup(groupId: String): List<Profile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<Profile>)

    @Update
    suspend fun updateProfile(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("DELETE FROM profiles WHERE id IN (:ids)")
    suspend fun deleteProfiles(ids: Set<String>)

    @Query("DELETE FROM profiles WHERE groupId = :groupId")
    suspend fun deleteProfilesByGroup(groupId: String)
}

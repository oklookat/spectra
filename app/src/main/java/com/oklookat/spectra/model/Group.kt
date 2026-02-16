package com.oklookat.spectra.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String? = null,
    val isRemote: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalMinutes: Int = 15,
    val lastUpdated: Long = 0
) {
    companion object {
        const val ALL_GROUP_ID = "all_group"
        const val DEFAULT_GROUP_ID = "default_group"
    }
}

package com.example.deepseekchat.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.deepseekchat.data.local.dao.MessageDao
import com.example.deepseekchat.data.local.dao.SessionDao
import com.example.deepseekchat.data.local.dao.UserProfilePresetDao
import com.example.deepseekchat.data.local.entity.MessageEntity
import com.example.deepseekchat.data.local.entity.SessionEntity
import com.example.deepseekchat.data.local.entity.UserProfilePresetEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class, UserProfilePresetEntity::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun userProfilePresetDao(): UserProfilePresetDao
}

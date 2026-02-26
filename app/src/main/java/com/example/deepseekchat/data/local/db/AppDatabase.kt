package com.example.deepseekchat.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.deepseekchat.data.local.dao.MessageDao
import com.example.deepseekchat.data.local.dao.SessionDao
import com.example.deepseekchat.data.local.entity.MessageEntity
import com.example.deepseekchat.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}

package com.personalai.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun roleToString(role: MessageRole): String = role.name

    @TypeConverter
    fun stringToRole(value: String): MessageRole = MessageRole.valueOf(value)
}

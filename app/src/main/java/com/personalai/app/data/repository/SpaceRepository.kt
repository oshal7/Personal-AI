package com.personalai.app.data.repository

import com.personalai.app.data.db.SpaceDao
import com.personalai.app.data.db.SpaceEntity
import kotlinx.coroutines.flow.Flow

class SpaceRepository(private val dao: SpaceDao) {

    fun observeSpaces(): Flow<List<SpaceEntity>> = dao.observeAll()

    suspend fun getSpace(id: Long): SpaceEntity? = dao.getById(id)

    suspend fun addSpace(name: String, skillFileName: String?, skillContent: String): Long =
        dao.insert(
            SpaceEntity(
                name = name,
                skillFileName = skillFileName,
                skillContent = skillContent,
                createdAtMillis = System.currentTimeMillis(),
            )
        )

    suspend fun deleteSpace(id: Long) = dao.deleteById(id)
}

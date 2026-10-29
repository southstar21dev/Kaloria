package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.database.model.asExternalModel
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

interface GoalsRepository {
    val goals: Flow<Goals>
    suspend fun save(goals: Goals)
}

internal class OfflineFirstGoalsRepository @Inject constructor(
    private val dao: GoalsDao,
    private val clock: Clock,
    private val sync: SyncRequester,
) : GoalsRepository {
    override val goals = dao.observe().map { it?.asExternalModel() ?: Goals() }

    override suspend fun save(goals: Goals) {
        val id = dao.current()?.id ?: Ids.newId()
        dao.upsert(goals.asEntity(id, updatedAt = clock.millis()))
        sync.requestSync()
    }
}

package com.helper.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY brand, model")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun find(id: String): VehicleEntity?

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)
}

@Dao
interface AssistanceDao {
    @Query("SELECT * FROM assistances WHERE localId = :localId")
    fun observe(localId: String): Flow<AssistanceEntity?>

    @Query("SELECT * FROM assistances WHERE localId = :localId")
    suspend fun find(localId: String): AssistanceEntity?

    /** El pedido activo es el último que no terminó (incluye los que están sin enviar). */
    @Query(
        "SELECT * FROM assistances WHERE status NOT IN ('EXPIRED', 'COMPLETED', 'CANCELLED', 'UNMATCHED') " +
            "ORDER BY confirmedAtMillis DESC LIMIT 1",
    )
    fun observeActive(): Flow<AssistanceEntity?>

    @Query("SELECT * FROM assistances WHERE status = 'NOT_SENT' ORDER BY confirmedAtMillis")
    suspend fun pending(): List<AssistanceEntity>

    @Upsert
    suspend fun upsert(assistance: AssistanceEntity)

    @Query("DELETE FROM assistances WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY distanceMeters")
    fun observeAll(): Flow<List<OfferEntity>>

    @Query("DELETE FROM offers")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(offers: List<OfferEntity>)

    @Query("DELETE FROM offers WHERE assistanceId = :assistanceId")
    suspend fun delete(assistanceId: String)

    @Transaction
    suspend fun replaceAll(offers: List<OfferEntity>) {
        clear()
        upsertAll(offers)
    }
}

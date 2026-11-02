package com.helper.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Fuente de verdad local: la UI siempre lee de acá, también sin conexión. */
@Database(
    entities = [VehicleEntity::class, AssistanceEntity::class, OfferEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HelperDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun assistanceDao(): AssistanceDao
    abstract fun offerDao(): OfferDao
}

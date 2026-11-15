package com.helper.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Fuente de verdad local: la UI siempre lee de acá, también sin conexión. */
@Database(
    entities = [VehicleEntity::class, AssistanceEntity::class, OfferEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class HelperDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun assistanceDao(): AssistanceDao
    abstract fun offerDao(): OfferDao
}

/** v2: tiempo estimado y ubicación del mecánico asignado, y ubicación del repartidor en las ofertas. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assistances ADD COLUMN mechanicEtaMinutes INTEGER")
        db.execSQL("ALTER TABLE assistances ADD COLUMN mechanicLatitude REAL")
        db.execSQL("ALTER TABLE assistances ADD COLUMN mechanicLongitude REAL")
        db.execSQL("ALTER TABLE offers ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE offers ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE offers ADD COLUMN etaMinutes INTEGER")
        db.execSQL("ALTER TABLE offers ADD COLUMN distanceByRoad INTEGER NOT NULL DEFAULT 0")
    }
}

package com.helper.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Fuente de verdad local: la UI siempre lee de acá, también sin conexión. */
@Database(
    entities = [VehicleEntity::class, AssistanceEntity::class, OfferEntity::class, StatusChangeEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class HelperDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun assistanceDao(): AssistanceDao
    abstract fun offerDao(): OfferDao
    abstract fun statusChangeDao(): StatusChangeDao
}

/** v4: teléfono del mecánico asignado, para el botón "Llamar". */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assistances ADD COLUMN mechanicPhone TEXT")
    }
}

/** v3: historial de estados de cada pedido, para verlo también sin conexión. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `status_changes` (`assistanceLocalId` TEXT NOT NULL, `fromStatus` TEXT, " +
                "`toStatus` TEXT NOT NULL, `changedAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`assistanceLocalId`, `toStatus`, `changedAtMillis`))",
        )
    }
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

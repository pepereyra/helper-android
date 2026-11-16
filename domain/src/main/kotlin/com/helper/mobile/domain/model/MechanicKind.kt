package com.helper.mobile.domain.model

/** [MOBILE] va hasta donde está el repartidor; [WORKSHOP] es un taller fijo al que el repartidor lleva el vehículo. */
enum class MechanicKind {
    MOBILE,
    WORKSHOP,
    ;

    companion object {
        /** Lo que no se reconoce (o un perfil viejo sin el dato) se trata como mecánico móvil. */
        fun from(value: String?): MechanicKind = entries.firstOrNull { it.name == value } ?: MOBILE
    }
}

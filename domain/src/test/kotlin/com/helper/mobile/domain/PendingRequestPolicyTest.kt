package com.helper.mobile.domain

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.policy.PendingRequestPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PendingRequestPolicyTest {

    private val policy = PendingRequestPolicy()
    private val confirmedAt = Instant.parse("2026-11-02T15:00:00Z")
    private val pending = Assistance(
        localId = "a1", remoteId = null, vehicleId = "v1", vehicleType = VehicleType.BICYCLE,
        problemCode = "FLAT_TIRE", description = null, location = OBELISCO,
        confirmedAt = confirmedAt, status = AssistanceStatus.NOT_SENT,
    )

    // ~500 m al norte del Obelisco
    private val farAway = GeoPoint(-34.5992, -58.3816)
    // ~50 m
    private val nearby = GeoPoint(-34.6033, -58.3816)

    @Test
    fun `se envia si todavia no pasaron 15 minutos`() {
        assertEquals(Decision.Send, policy.decide(pending, confirmedAt.plus(Duration.ofMinutes(14)), OBELISCO))
    }

    @Test
    fun `vence a los 15 minutos y ya no se envia`() {
        assertEquals(Decision.Expire, policy.decide(pending, confirmedAt.plus(Duration.ofMinutes(15)), OBELISCO))
        assertEquals(Decision.Expire, policy.decide(pending, confirmedAt.plus(Duration.ofHours(2)), null))
    }

    @Test
    fun `antes de 5 minutos no se revisa la ubicacion aunque se haya movido`() {
        assertEquals(Decision.Send, policy.decide(pending, confirmedAt.plus(Duration.ofMinutes(4)), farAway))
    }

    @Test
    fun `despues de 5 minutos, si se movio mas de 300 m, pide reconfirmar`() {
        val decision = policy.decide(pending, confirmedAt.plus(Duration.ofMinutes(6)), farAway)
        assertTrue(decision is Decision.ReconfirmLocation)
        assertEquals(farAway, (decision as Decision.ReconfirmLocation).current)
    }

    @Test
    fun `despues de 5 minutos, si sigue cerca, se envia`() {
        assertEquals(Decision.Send, policy.decide(pending, confirmedAt.plus(Duration.ofMinutes(6)), nearby))
    }

    @Test
    fun `la distancia con haversine es razonable`() {
        val meters = OBELISCO.distanceTo(farAway)
        assertTrue("fue $meters", meters in 450.0..550.0)
    }
}

package com.helper.mobile.domain

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.MechanicJob
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.JobActionResult
import com.helper.mobile.domain.usecase.AdvanceJobUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvanceJobUseCaseTest {

    private fun job(status: AssistanceStatus) =
        MechanicJob("a1", "Pinchadura", VehicleType.BICYCLE, OBELISCO, status)

    @Test
    fun `un trabajo aceptado se empieza`() = runTest {
        val jobs = FakeJobs()
        AdvanceJobUseCase(jobs)(job(AssistanceStatus.MATCHED))
        assertEquals(listOf("start:a1"), jobs.calls)
    }

    @Test
    fun `un trabajo en curso se termina`() = runTest {
        val jobs = FakeJobs(JobActionResult.Done(AssistanceStatus.COMPLETED))
        val result = AdvanceJobUseCase(jobs)(job(AssistanceStatus.IN_PROGRESS))
        assertEquals(listOf("complete:a1"), jobs.calls)
        assertEquals(JobActionResult.Done(AssistanceStatus.COMPLETED), result)
    }

    @Test
    fun `un trabajo terminado no llama al servidor`() = runTest {
        val jobs = FakeJobs()
        val result = AdvanceJobUseCase(jobs)(job(AssistanceStatus.COMPLETED))
        assertTrue(result is JobActionResult.Conflict)
        assertTrue(jobs.calls.isEmpty())
        assertNull(AdvanceJobUseCase.nextStepLabel(AssistanceStatus.COMPLETED))
    }
}

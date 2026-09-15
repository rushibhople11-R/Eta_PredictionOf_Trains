package org.example.project

import org.example.project.model.ConnectingService
import org.example.project.model.ConnectionEngine
import org.example.project.model.ServiceType
import org.example.project.model.TransferFeasibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionEngineTest {

    @Test
    fun testSafeTransferEvaluation() {
        val connectingTrain = ConnectingService(
            serviceNumber = "12024",
            serviceName = "Jan Shatabdi",
            serviceType = ServiceType.TRAIN,
            interchangeStationCode = "PNBE",
            departureTime = "06:00",
            departurePlatform = "Pf 2"
        )

        // Incoming train arrives at 04:30. 90m diff - 10m walk = 80m buffer -> SAFE
        val result = ConnectionEngine.evaluateTransfer(
            incomingArrivalEtaStr = "04:30",
            connectingService = connectingTrain,
            platformWalkBufferMinutes = 10
        )

        assertEquals(TransferFeasibility.SAFE, result.feasibility)
        assertTrue(result.feasibilityScore >= 85)
        assertEquals(80, result.transferBufferMinutes)
    }

    @Test
    fun testTightTransferEvaluation() {
        val connectingCab = ConnectingService(
            serviceNumber = "CAB-1",
            serviceName = "Station Taxi",
            serviceType = ServiceType.CAB,
            interchangeStationCode = "PRYJ",
            departureTime = "23:45"
        )

        // Incoming train delayed, arrives at 23:25. 20m diff - 10m walk = 10m buffer -> TIGHT_RISK
        val result = ConnectionEngine.evaluateTransfer(
            incomingArrivalEtaStr = "23:25",
            connectingService = connectingCab,
            platformWalkBufferMinutes = 10
        )

        assertEquals(TransferFeasibility.TIGHT_RISK, result.feasibility)
        assertEquals(10, result.transferBufferMinutes)
    }

    @Test
    fun testCriticalMissEvaluation() {
        val connectingTrain = ConnectingService(
            serviceNumber = "14216",
            serviceName = "Ganga Gomti",
            serviceType = ServiceType.TRAIN,
            interchangeStationCode = "PRYJ",
            departureTime = "23:55"
        )

        // Incoming train delayed to 00:05. Buffer negative -> CRITICAL_MISS
        val result = ConnectionEngine.evaluateTransfer(
            incomingArrivalEtaStr = "00:05",
            connectingService = connectingTrain,
            platformWalkBufferMinutes = 10
        )

        assertEquals(TransferFeasibility.CRITICAL_MISS, result.feasibility)
        assertTrue(result.transferBufferMinutes < 0)
    }
}

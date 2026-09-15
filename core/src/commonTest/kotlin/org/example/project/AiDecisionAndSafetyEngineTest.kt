package org.example.project

import kotlin.test.*
import org.example.project.engine.AiDecisionAndSafetyEngine
import org.example.project.engine.AlertCategory
import org.example.project.engine.ControllerDecisionType

class AiDecisionAndSafetyEngineTest {

    @Test
    fun testAlertsCategories() {
        val alerts = AiDecisionAndSafetyEngine.getDefaultAlerts()
        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.any { it.category == AlertCategory.ACTIVE })
        assertTrue(alerts.any { it.category == AlertCategory.WARNING })
        assertTrue(alerts.any { it.category == AlertCategory.RESOLVED })
    }

    @Test
    fun testOdhaCongestionWorkflow() {
        val scenario = AiDecisionAndSafetyEngine.getOdhaCongestionScenario()
        assertEquals("12109", scenario.affectedTrainNumber)
        assertEquals("22223", scenario.conflictTrainNumber)

        // Generate AI Recommendation
        val recommendation = AiDecisionAndSafetyEngine.generateAiRecommendation(scenario)
        assertEquals("12109", recommendation.targetTrainNumber)
        assertEquals(7, recommendation.holdMinutes)
        assertEquals(19, recommendation.expectedSavingsMinutes)
        assertTrue(recommendation.confidencePercent >= 90)

        // Safety Engine Audit
        val safetyAudit = AiDecisionAndSafetyEngine.auditSafety(recommendation)
        assertTrue(safetyAudit.isSafe, "AI Recommendation must pass safety checks")
        assertTrue(safetyAudit.safetyScorePercent >= 95)
        assertEquals(4, safetyAudit.ruleChecks.size)
        assertTrue(safetyAudit.ruleChecks.all { it.isPassed })
    }

    @Test
    fun testCorridorThroughputMetrics() {
        val throughputList = AiDecisionAndSafetyEngine.getCorridorThroughput()
        assertEquals(5, throughputList.size)
        assertTrue(throughputList.any { it.bottleneckStatus == "BOTTLENECK" })
        assertTrue(throughputList.any { it.sectionId == "SEC_DVL_NK_ODH" })
    }

    @Test
    fun testPerformanceReports() {
        val reports = AiDecisionAndSafetyEngine.getPerformanceReports()
        assertEquals(7, reports.size)
        assertTrue(reports.any { it.punctualityGrade == "A+" })
    }
}

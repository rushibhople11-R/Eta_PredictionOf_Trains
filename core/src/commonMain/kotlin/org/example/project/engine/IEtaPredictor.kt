package org.example.project.engine

import org.example.project.model.ModelArchitecture
import org.example.project.model.PredictionResult
import org.example.project.model.TrainJourneyFeatureVector

/**
 * Pluggable Machine Learning & Heuristic Prediction Strategy Interface.
 *
 * Implementations can range from deterministic physics models to
 * LightGBM gradient boosted trees, ONNX runtime transformers, or Graph Neural Networks.
 */
interface IEtaPredictor {
    val modelArchitecture: ModelArchitecture
    val modelVersion: String

    /**
     * Computes the arrival ETA, predicted delay, and quantile intervals
     * for a target station based on the supplied feature vector.
     */
    fun predictStationArrival(
        features: TrainJourneyFeatureVector,
        scheduledArrivalEpochMillis: Long
    ): PredictionResult
}

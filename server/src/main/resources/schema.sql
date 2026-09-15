-- ====================================================================
-- RailNexus Normalized Enterprise Database Schema (PostgreSQL / TimescaleDB)
-- Production DDL with Relational Constraints, Foreign Keys & Indices
-- ====================================================================

-- 1. Stations Master
CREATE TABLE IF NOT EXISTS stations (
    station_code VARCHAR(10) PRIMARY KEY,
    station_name VARCHAR(100) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    division VARCHAR(50) NOT NULL DEFAULT 'Bhusawal',
    zone VARCHAR(10) NOT NULL DEFAULT 'CR',
    is_junction BOOLEAN NOT NULL DEFAULT FALSE,
    total_platforms INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stations_geo ON stations(latitude, longitude);

-- 2. Trains Master
CREATE TABLE IF NOT EXISTS trains (
    train_number VARCHAR(10) PRIMARY KEY,
    train_name VARCHAR(100) NOT NULL,
    train_type VARCHAR(30) NOT NULL,
    source_station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    destination_station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    scheduled_departure TIME NOT NULL,
    scheduled_arrival TIME NOT NULL,
    total_distance_km INT NOT NULL,
    priority_score INT NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Timetable Schedules (Sequence junction table)
CREATE TABLE IF NOT EXISTS schedules (
    schedule_id BIGSERIAL PRIMARY KEY,
    train_number VARCHAR(10) NOT NULL REFERENCES trains(train_number) ON DELETE CASCADE,
    station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    stop_sequence INT NOT NULL,
    scheduled_arrival TIME NOT NULL,
    scheduled_departure TIME NOT NULL,
    distance_from_source_km INT NOT NULL,
    planned_dwell_minutes INT NOT NULL DEFAULT 2,
    section_slack_minutes INT NOT NULL DEFAULT 0,
    platform_number INT DEFAULT 1,
    is_single_track_segment BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_train_sequence UNIQUE (train_number, stop_sequence),
    CONSTRAINT uq_train_station UNIQUE (train_number, station_code)
);

CREATE INDEX IF NOT EXISTS idx_schedules_train_seq ON schedules(train_number, stop_sequence);

-- 4. Daily Train Runs (Journey instances)
CREATE TABLE IF NOT EXISTS train_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    train_number VARCHAR(10) NOT NULL REFERENCES trains(train_number),
    run_date DATE NOT NULL,
    operational_status VARCHAR(20) NOT NULL DEFAULT 'IN_TRANSIT',
    actual_start_time TIMESTAMPTZ,
    actual_end_time TIMESTAMPTZ,
    current_delay_minutes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_train_date UNIQUE (train_number, run_date)
);

CREATE INDEX IF NOT EXISTS idx_train_runs_status ON train_runs(run_date, operational_status);

-- 5. High-Frequency Live Positions (Time-series)
CREATE TABLE IF NOT EXISTS train_positions (
    position_id BIGSERIAL,
    run_id UUID NOT NULL REFERENCES train_runs(run_id) ON DELETE CASCADE,
    current_segment_block VARCHAR(100) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    speed_kmph NUMERIC(5, 2) NOT NULL DEFAULT 0.0,
    telemetry_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (position_id, telemetry_timestamp)
);

CREATE INDEX IF NOT EXISTS idx_positions_run_time ON train_positions(run_id, telemetry_timestamp DESC);

-- 6. Actual Movements (Ground Truth Station Arrivals/Departures)
CREATE TABLE IF NOT EXISTS actual_movements (
    movement_id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES train_runs(run_id) ON DELETE CASCADE,
    station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    actual_arrival TIMESTAMPTZ,
    actual_departure TIMESTAMPTZ,
    observed_delay_minutes INT NOT NULL DEFAULT 0,
    actual_dwell_minutes INT DEFAULT 2,
    actual_platform_used INT DEFAULT 1,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_run_station_movement UNIQUE (run_id, station_code)
);

CREATE INDEX IF NOT EXISTS idx_movements_run ON actual_movements(run_id);

-- 7. AI Dynamic Predictions
CREATE TABLE IF NOT EXISTS eta_predictions (
    prediction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES train_runs(run_id) ON DELETE CASCADE,
    target_station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    predicted_eta TIMESTAMPTZ NOT NULL,
    prediction_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confidence_score NUMERIC(5, 2) NOT NULL,
    predicted_delay_minutes INT NOT NULL,
    quantile_p10_delay INT NOT NULL,
    quantile_p90_delay INT NOT NULL,
    primary_explanation TEXT,
    model_version VARCHAR(30) NOT NULL DEFAULT 'v2.4.1-lgbm-quantile'
);

CREATE INDEX IF NOT EXISTS idx_predictions_active ON eta_predictions(run_id, target_station_code, prediction_timestamp DESC);

-- 8. Delay Events (SHAP-Style Root Cause Attribution)
CREATE TABLE IF NOT EXISTS delay_events (
    event_id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES train_runs(run_id) ON DELETE CASCADE,
    station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    delay_duration_minutes INT NOT NULL,
    primary_reason_category VARCHAR(50) NOT NULL,
    detailed_explanation TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_delay_events_run ON delay_events(run_id);

-- 9. Active Corridor Alerts & Caution Orders
CREATE TABLE IF NOT EXISTS alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    run_id UUID REFERENCES train_runs(run_id) ON DELETE SET NULL,
    station_code VARCHAR(10) REFERENCES stations(station_code),
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    alert_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_alerts_active ON alerts(is_resolved, severity);

-- 10. Continuous Model Performance Monitoring
CREATE TABLE IF NOT EXISTS model_performance (
    evaluation_id BIGSERIAL PRIMARY KEY,
    prediction_id UUID NOT NULL REFERENCES eta_predictions(prediction_id) ON DELETE CASCADE,
    actual_movement_id BIGINT NOT NULL REFERENCES actual_movements(movement_id) ON DELETE CASCADE,
    absolute_error_minutes NUMERIC(6, 2) NOT NULL,
    squared_error NUMERIC(10, 2) NOT NULL,
    model_version VARCHAR(30) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_model_eval_version ON model_performance(model_version, evaluated_at);

-- 11. Controller What-If Scenarios
CREATE TABLE IF NOT EXISTS what_if_scenarios (
    scenario_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES train_runs(run_id) ON DELETE CASCADE,
    regulation_station_code VARCHAR(10) NOT NULL REFERENCES stations(station_code),
    hypothetical_delay_minutes INT NOT NULL,
    predicted_downstream_impact_minutes INT NOT NULL,
    corridor_minutes_saved INT NOT NULL,
    safety_audit_status VARCHAR(30) NOT NULL DEFAULT 'SAFE_TO_EXECUTE',
    controller_action VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    simulated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_what_if_run ON what_if_scenarios(run_id, simulated_at DESC);

-- ============================================================================
-- Jörmungandr Database Analytics Suite — Multi-Dialect Schema & Test Data
-- Supported Dialects: PostgreSQL, MySQL, SQLite, Snowflake
-- ============================================================================

-- Table 1: Enterprise Users & Organization Tenancy
CREATE TABLE IF NOT EXISTS enterprise_tenants (
    tenant_id VARCHAR(36) PRIMARY KEY,
    organization_name VARCHAR(120) NOT NULL,
    subscription_plan VARCHAR(32) DEFAULT 'ENTERPRISE_PRO',
    max_seats INT DEFAULT 50,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Table 2: Analytical Event Logs (Partitioned simulation)
CREATE TABLE IF NOT EXISTS lakehouse_query_logs (
    log_id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    query_hash VARCHAR(64) NOT NULL,
    query_engine VARCHAR(32) NOT NULL, -- 'DuckDB', 'Trino', 'Spark', 'PostgreSQL'
    execution_time_ms INT NOT NULL,
    bytes_scanned BIGINT DEFAULT 0,
    rows_returned INT DEFAULT 0,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES enterprise_tenants(tenant_id)
);

-- Seed Data for UI Data Grid inspection
INSERT INTO enterprise_tenants (tenant_id, organization_name, subscription_plan, max_seats) VALUES
('TEN-001', 'Acme Data Systems', 'ENTERPRISE_PRO', 100),
('TEN-002', 'Helios Analytics Corp', 'ENTERPRISE_ULTIMATE', 250),
('TEN-003', 'Nordic FinTech Labs', 'DEV_TEAM', 25);

INSERT INTO lakehouse_query_logs (log_id, tenant_id, query_hash, query_engine, execution_time_ms, bytes_scanned, rows_returned) VALUES
(1001, 'TEN-001', 'a1b2c3d4e5', 'DuckDB', 14, 1048576, 500),
(1002, 'TEN-001', 'f6g7h8i9j0', 'PostgreSQL', 85, 4194304, 1200),
(1003, 'TEN-002', 'k1l2m3n4o5', 'DuckDB', 8, 524288, 50),
(1004, 'TEN-003', 'p6q7r8s9t0', 'Trino', 420, 104857600, 45000);

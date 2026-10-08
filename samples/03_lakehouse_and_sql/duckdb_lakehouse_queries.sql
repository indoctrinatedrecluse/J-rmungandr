-- ============================================================================
-- Jörmungandr DuckDB In-Memory Lakehouse Queries
-- Tests: Embedded DuckDB engine, CSV/Parquet scanning, Window functions, CTEs
-- ============================================================================

-- 1. Scan synthetic CSV datasets directly without loading into a database
SELECT 
    country,
    COUNT(*) AS total_customers,
    ROUND(AVG(annual_spend_usd), 2) AS avg_annual_spend,
    ROUND(AVG(credit_score), 1) AS avg_credit_score,
    ROUND(SUM(CASE WHEN is_churned = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS churn_rate_pct
FROM read_csv_auto('samples/02_datasets/customers.csv')
GROUP BY country
ORDER BY avg_annual_spend DESC;

-- 2. Financial transactions rolling anomaly analysis with Window Functions
WITH ranked_transactions AS (
    SELECT 
        tx_id,
        timestamp_utc,
        merchant_category,
        amount,
        risk_score,
        AVG(amount) OVER (
            PARTITION BY merchant_category 
            ORDER BY timestamp_utc 
            ROWS BETWEEN 5 PRECEDING AND CURRENT ROW
        ) AS rolling_avg_amount,
        RANK() OVER (
            PARTITION BY merchant_category 
            ORDER BY amount DESC
        ) AS category_amount_rank
    FROM read_csv_auto('samples/02_datasets/financial_transactions.csv')
)
SELECT 
    tx_id,
    merchant_category,
    amount,
    ROUND(rolling_avg_amount, 2) AS rolling_avg_amount,
    category_amount_rank,
    risk_score
FROM ranked_transactions
WHERE category_amount_rank <= 3
ORDER BY merchant_category, category_amount_rank;

-- 3. In-memory Parquet introspection (when parquet files are present)
-- SELECT 
--     * 
-- FROM read_parquet('samples/03_lakehouse_and_sql/mock_delta_table/*.parquet')
-- LIMIT 10;

-- Marts: Aggregated daily revenue fact table
-- References stg_orders staging model
with orders as (
    select * from {{ ref('stg_orders') }}
)
select
    date_trunc('day', order_timestamp) as revenue_date,
    merchant_category,
    count(distinct order_id) as total_transactions,
    count(distinct customer_id) as unique_purchasers,
    sum(order_amount_usd) as gross_revenue_usd,
    avg(order_amount_usd) as average_order_value_usd
from orders
where not is_fraudulent
group by 1, 2

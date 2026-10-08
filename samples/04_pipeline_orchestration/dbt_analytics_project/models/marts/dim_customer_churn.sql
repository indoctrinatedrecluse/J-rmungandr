-- Marts: Customer Churn and LTV dimension table
-- Joins stg_customers with aggregated order metrics from stg_orders
with customers as (
    select * from {{ ref('stg_customers') }}
),
customer_orders as (
    select
        customer_id,
        count(distinct order_id) as lifetime_order_count,
        sum(order_amount_usd) as lifetime_spend_usd,
        max(order_timestamp) as last_order_timestamp
    from {{ ref('stg_orders') }}
    group by 1
)
select
    c.customer_id,
    c.country,
    c.tenure_months,
    c.credit_score,
    c.is_churned,
    coalesce(co.lifetime_order_count, 0) as lifetime_order_count,
    coalesce(co.lifetime_spend_usd, 0.0) as lifetime_spend_usd,
    co.last_order_timestamp,
    case
        when c.tenure_months > 24 and coalesce(co.lifetime_spend_usd, 0) > 5000 then 'VIP_LOYAL'
        when c.is_churned = 1 then 'CHURNED'
        else 'STANDARD'
    end as customer_segment
from customers c
left join customer_orders co on c.customer_id = co.customer_id

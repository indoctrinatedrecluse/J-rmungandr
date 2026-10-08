-- Staging model for raw customer data
with source_customers as (
    select * from {{ source('raw_store', 'customers') }}
)
select
    customer_id,
    lower(trim(email)) as email,
    country,
    tenure_months,
    credit_score,
    annual_spend_usd,
    is_churned,
    current_timestamp as staged_at
from source_customers

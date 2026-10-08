-- Staging model for raw transactions and orders
with source_orders as (
    select * from {{ source('raw_store', 'financial_transactions') }}
)
select
    tx_id as order_id,
    account_id as customer_id,
    merchant_category,
    amount as order_amount_usd,
    currency,
    cast(timestamp_utc as timestamp) as order_timestamp,
    case when is_flagged_fraud = 1 then true else false end as is_fraudulent
from source_orders

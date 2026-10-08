"""
Dataset Generator for Jörmungandr Testing & Evaluation
Synthesizes tabular CSV, JSON streaming buffers, and distribution drift datasets.
"""

import os
import csv
import json
import random
import math
from datetime import datetime, timedelta

def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out_dir, exist_ok=True)
    random.seed(42)

    # 1. Customers Dataset (Tabular Grid & Correlation Testing)
    customers_path = os.path.join(out_dir, "customers.csv")
    first_names = ["Alice", "Bob", "Charlie", "Diana", "Evan", "Fiona", "George", "Hannah", "Ian", "Julia"]
    last_names = ["Smith", "Vance", "Kovacs", "Nakamoto", "Duval", "Larsson", "O'Connor", "Chen", "Al-Mansoor", "Silva"]
    countries = ["US", "DE", "GB", "FR", "JP", "CA", "AU", "NL", "SE", "CH"]

    with open(customers_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["customer_id", "full_name", "email", "country", "tenure_months", "credit_score", "annual_spend_usd", "churn_risk_score", "is_churned"])
        for i in range(1, 251):
            fn = random.choice(first_names)
            ln = random.choice(last_names)
            name = f"{fn} {ln}"
            email = f"{fn.lower()}.{ln.lower()}{i}@example.com"
            country = random.choice(countries)
            tenure = random.randint(1, 72)
            credit_score = int(random.gauss(710, 60))
            credit_score = max(350, min(850, credit_score))
            spend = round(max(200.0, random.gauss(4500, 1800) + tenure * 50), 2)
            churn_risk = round(max(0.01, min(0.99, random.random() * (0.8 if tenure < 12 else 0.3))), 3)
            churned = 1 if churn_risk > 0.65 else 0
            writer.writerow([f"CUST-{i:05d}", name, email, country, tenure, credit_score, spend, churn_risk, churned])
    print(f"Generated: {customers_path} (250 rows)")

    # 2. Financial Transactions Dataset
    transactions_path = os.path.join(out_dir, "financial_transactions.csv")
    merchants = ["Cloud Hosting", "Hardware Electronics", "Aviation Booking", "Retail Superstore", "SaaS Subscription", "Fine Dining"]
    currencies = ["USD", "EUR", "GBP", "JPY", "CAD"]

    base_time = datetime(2026, 10, 1, 8, 0, 0)
    with open(transactions_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["tx_id", "timestamp_utc", "account_id", "merchant_category", "amount", "currency", "risk_score", "is_flagged_fraud"])
        for i in range(1, 501):
            tx_time = base_time + timedelta(minutes=i * 7 + random.randint(0, 5))
            acc_id = f"ACC-{random.randint(100, 999)}"
            category = random.choice(merchants)
            curr = random.choice(currencies)
            amount = round(random.expovariate(1/120.0) + 5.0, 2)
            risk = round(min(1.0, max(0.0, random.gauss(0.15, 0.12) if amount < 500 else random.gauss(0.70, 0.15))), 4)
            fraud = 1 if risk > 0.80 else 0
            writer.writerow([f"TX-{100000 + i}", tx_time.isoformat() + "Z", acc_id, category, amount, curr, risk, fraud])
    print(f"Generated: {transactions_path} (500 rows)")

    # 3. IoT Sensor Telemetry (Streaming Ingress Simulator)
    telemetry_path = os.path.join(out_dir, "sensor_telemetry.json")
    telemetry_events = []
    device_ids = ["TURBINE-01", "TURBINE-02", "PUMP-NORTH", "COMPRESSOR-04", "GENERATOR-PRIMARY"]
    for i in range(150):
        evt_time = (base_time + timedelta(seconds=i * 10)).isoformat() + "Z"
        dev = random.choice(device_ids)
        temp_c = round(random.gauss(72.5, 4.2), 2)
        vibration_hz = round(random.gauss(48.0, 6.5), 2)
        pressure_kpa = round(random.gauss(310.0, 15.0), 2)
        battery_pct = round(max(5.0, 100.0 - (i * 0.15)), 1)
        alert = "CRITICAL" if temp_c > 82.0 or vibration_hz > 62.0 else "NOMINAL"
        telemetry_events.append({
            "timestamp": evt_time,
            "device_id": dev,
            "metrics": {
                "temperature_celsius": temp_c,
                "vibration_frequency_hz": vibration_hz,
                "pressure_kpa": pressure_kpa,
                "battery_percentage": battery_pct
            },
            "status": alert
        })
    with open(telemetry_path, "w", encoding="utf-8") as f:
        json.dump(telemetry_events, f, indent=2)
    print(f"Generated: {telemetry_path} (150 telemetry packets)")

    # 4. Distribution Drift Datasets (Train vs Test / Prod vs Staging)
    # reference_train: normal latency (mean 45ms, std 8ms), normal error rate (1.2%)
    ref_path = os.path.join(out_dir, "reference_train.csv")
    with open(ref_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["sample_id", "endpoint_latency_ms", "cpu_utilization_pct", "memory_rss_mb", "error_rate_pct"])
        for i in range(1, 401):
            lat = round(random.gauss(45.0, 8.0), 2)
            cpu = round(max(5.0, min(95.0, random.gauss(38.0, 10.0))), 2)
            mem = round(random.gauss(512.0, 40.0), 1)
            err = round(max(0.0, random.gauss(1.2, 0.4)), 3)
            writer.writerow([f"SAMP-{i:04d}", lat, cpu, mem, err])
    print(f"Generated: {ref_path} (400 baseline reference rows)")

    # current_production: drifted latency (mean 82ms, std 18ms), elevated error rate (6.8%)
    curr_path = os.path.join(out_dir, "current_production.csv")
    with open(curr_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["sample_id", "endpoint_latency_ms", "cpu_utilization_pct", "memory_rss_mb", "error_rate_pct"])
        for i in range(1, 401):
            lat = round(random.gauss(82.0, 18.0), 2)  # High drift
            cpu = round(max(10.0, min(99.0, random.gauss(68.0, 14.0))), 2)  # Elevated CPU
            mem = round(random.gauss(780.0, 95.0), 1)  # Memory growth
            err = round(max(0.0, random.gauss(6.8, 1.8)), 3)  # Severe error spike
            writer.writerow([f"PROD-{i:04d}", lat, cpu, mem, err])
    print(f"Generated: {curr_path} (400 production comparison rows — tests PSI & K-S drift)")

if __name__ == "__main__":
    main()

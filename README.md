# 🐛 Country-Based Product API — Bug Fix Challenge

## 📋 Assignment Overview

We've implemented a Ktor service for managing products and discounts, but **the tests are failing**. Your task is to **identify and fix the bugs** causing the test failures.

**Important:** Do NOT modify the test expectations — they are correct and represent the actual business requirements. Fix the implementation to make the tests pass.

## ⏱️ Time Expectation
This should take approximately **1-2 hours**. We value your time!

---

## 🎯 Your Task

1. **Run the tests** — you'll see multiple failures
2. **Analyze the failures** — understand what's expected vs. what's happening
3. **Fix the bugs** — modify the implementation code (NOT the tests)
4. **Verify** — ensure all tests pass

### Rules:
- ✅ Fix implementation code
- ✅ Add missing configuration
- ✅ Fix database/concurrency issues
- ❌ Do NOT change test assertions or expectations
- ❌ Do NOT simplify the requirements

---

## 📦 What the Service Should Do

The Product API:
- Stores products with country-specific VAT rates
- Calculates final prices: `finalPrice = basePrice × (1 - totalDiscount) × (1 + VAT)`
- Applies discounts idempotently (same discount cannot be applied twice)
- **Handles concurrent discount requests safely** (critical requirement!)

### Country VAT Rules
| Country | VAT |
|----------|-----|
| Sweden | 25% |
| Germany | 19% |
| France | 20% |

**Note:** The system should properly validate and handle requests for countries not in this table. Think about what the correct behavior should be.

---

## 📡 API Endpoints

### `GET /products?country={country}`
Returns all products for the given country with calculated final prices.

**Response:**
```json
[
  {
    "id": "prod-1",
    "name": "Laptop",
    "basePrice": 1000.0,
    "country": "Sweden",
    "discounts": [
      {"discountId": "SUMMER10", "percent": 10.0}
    ],
    "finalPrice": 1125.0
  }
]
```

### `PUT /products/{id}/discount`
Applies a discount to a product. Must be idempotent and concurrency-safe.

**Request:**
```json
{
  "discountId": "SUMMER10",
  "percent": 10.0
}
```

**Response:**
```json
{
  "id": "prod-1",
  "name": "Laptop",
  "basePrice": 1000.0,
  "country": "Sweden",
  "discounts": [
    {"discountId": "SUMMER10", "percent": 10.0}
  ],
  "finalPrice": 1125.0
}
```

---

## 🚀 Getting Started

### Prerequisites
- JDK 22
- Docker (for MongoDB via Testcontainers)

### Run Tests
```bash
cd discount
./gradlew test
```

You should see several test failures. Your job is to fix them!

---

## 🐛 Known Issues (Hints)

The tests are failing for various reasons. Some bugs are subtle, others are more obvious. Pay attention to:
- Serialization/deserialization of JSON
- Discount calculation logic
- Database constraints and indexes
- Race conditions under concurrent access
- **Edge cases and validation** — not all bugs cause test failures! Think critically about what could go wrong with invalid or unexpected inputs.

---

## 📬 Submission

When all tests pass:
1. Commit your changes with clear commit messages
2. Push to your fork or branch
3. Share the repository link

Add a brief `FIXES.md` explaining what bugs you found and how you fixed them.

---

✨ **Good luck! Remember: the tests are your specification.**

---

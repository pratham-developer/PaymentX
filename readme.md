# PaymentX — Technical Specification

> Campus closed-loop digital payment platform using NFC-enabled identity cards and secure digital wallets.

| Property | Value |
|---|---|
| Architecture | Event-Driven Modular Monolith |
| Currency | INR (Indian Rupee) |
| Audience | Backend Developers, Mobile Engineers, QA & DevOps |

---

## Table of Contents

1. [Project Details](#1-project-details)
2. [Database Schema](#2-database-schema)
3. [API Specification](#3-api-specification)
4. [Application Flows](#4-application-flows)
5. [System Architecture, Resilience & Guidelines](#5-system-architecture-resilience--guidelines)
6. [Development Phase Distribution](#6-development-phase-distribution)

---

## 1. Project Details

### 1.1 Project Overview

PaymentX is a closed-loop digital payment platform designed for campus environments. It facilitates cashless transactions between students and campus merchants using NFC-enabled identity cards linked to a secure digital wallet.

The system is engineered as an **Event-Driven Modular Monolith**, mimicking real-world banking architecture. It prioritizes data integrity, transactional atomicity, and enterprise-grade resilience through asynchronous messaging and automated background reconciliation.

### 1.2 Problem Statement

Campus payments currently lack secure integration with university identification systems. Legacy NFC ID cards broadcast static, untrusted UUIDs, leaving systems vulnerable to cloning and relay attacks.

PaymentX bridges this gap by decoupling the physical NFC tap from financial authorization — gating all payments behind ephemeral, server-issued Redis session tokens bound cryptographically to specific transaction amounts and attested merchant devices.

### 1.3 Scope of the Final Release

- **Architecture** — Event-Driven Modular Monolith enforcing strict Bounded Contexts (IAM, Ledger, Gateway, Transaction Engine).
- **Async Processing** — RabbitMQ integration for decoupling modules, managing webhook ingestion, and utilizing Dead Letter Queues (DLQ) for failure recovery.
- **Automated Reconciliation** — Cron-based background workers to guarantee internal ledger integrity and parity with external gateways.
- **Financial Core** — Ledger equipped with PostgreSQL Optimistic Locking, Mobile Payments on COTS (MPoC) secured PIN entry, and PSD2-inspired dynamic linking.
- **External Integration** — Wallet top-ups via Razorpay Orders API and automated merchant withdrawals via RazorpayX Payouts API.

### 1.4 User Roles & Responsibilities

**Student**
- Registers with university email and links a physical NFC card.
- Manages wallet balance via Razorpay top-ups.
- Authorizes payments securely via MPoC PIN entry on merchant devices.

**Merchant**
- Registers a business profile with optional GST Tax ID.
- Initiates payment requests via NFC scanning on an attested COTS Android device.
- Receives automated settlements via RazorpayX Payouts.

**Admin**
- System oversight, dispute resolution, and ledger reconciliation monitoring.

### 1.5 Assumptions & Constraints

| Constraint | Detail |
|---|---|
| Network | All transactions require active internet connectivity. Offline transaction queuing is explicitly out of scope. |
| Hardware | Students possess ISO 14443 compliant NFC cards. Merchants operate NFC-enabled Android devices (API level 19+). |
| Currency | System operates exclusively in INR (Indian Rupee). |
| Compliance | Sensitive external card data is offloaded entirely to the Razorpay Checkout SDK. |

---

## 2. Database Schema

| Engine | Technology | Justification |
|---|---|---|
| Persistent | PostgreSQL (isolated schemas per domain module) | ACID compliance, robust transaction support, row-level locking. |
| Ephemeral | Redis (keyspace-based) | Sub-millisecond session token management, automated TTL for OTPs, high-speed rate limiting. |
| Migration Standard | Flyway / Liquibase | Version-controlled schema changes. Manual execution across environments is strictly prohibited. |

### 2.1 IAM Module — Identity & Access Management

**`users`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK | Unique User ID. |
| email | VARCHAR(255) | UNIQUE, NOT NULL, INDEX | College (@vit.ac.in) or Business email. |
| password_hash | VARCHAR(255) | NOT NULL | Argon2id hash of login password. |
| phone | VARCHAR(20) | UNIQUE, NOT NULL, INDEX | E.164 format. |
| role | ENUM | NOT NULL | Values: `STUDENT`, `MERCHANT`, `ADMIN`. |
| is_email_verified | BOOLEAN | Default FALSE | Step 1 verification status. |
| is_phone_verified | BOOLEAN | Default FALSE | Step 2 verification status. |

**`session`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | PK | Auto-increment ID. |
| user_id | UUID | FK, INDEX | References `users.id`. |
| refresh_token_hash | VARCHAR(255) | UNIQUE, INDEX | SHA-256 hash of the secure token string. |
| device_fingerprint | VARCHAR(255) | NOT NULL | Cryptographic binding utilizing Google Play Integrity. |
| last_used_at | TIMESTAMP | Default NOW() | Audit timestamp. |

### 2.2 User Profiles

**`student_profile`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| user_id | UUID | PK, FK | References `users.id`. |
| full_name | VARCHAR(100) | NOT NULL | — |
| college_reg_no | VARCHAR(50) | UNIQUE, NOT NULL | University registration ID. |

**`merchant_profile`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| user_id | UUID | PK, FK | References `users.id`. |
| business_name | VARCHAR(100) | NOT NULL | — |
| gst_tax_id | BYTEA | NULLABLE | Encrypted at rest using AES-256-GCM. |
| razorpay_contact_id | VARCHAR(100) | NULLABLE | RazorpayX Contact ID required to create a Fund Account. |
| razorpay_fund_id | VARCHAR(100) | NULLABLE | RazorpayX Fund Account ID. |

### 2.3 Financial Core — Ledger Module

**`wallet`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK | — |
| user_id | UUID | FK, UNIQUE | 1:1 mapping with User. |
| balance | DECIMAL(19,4) | Default 0.0000 | High-precision numeric type. |
| pin_hash | VARCHAR(255) | NULLABLE | Bcrypt hashed transaction PIN. |
| status | ENUM | Default `ACTIVE` | Values: `ACTIVE`, `LOCKED` (security lockout). |
| pin_attempts | INTEGER | Default 0 | Tracks consecutive failed PIN entries. |
| version | BIGINT | Default 0 | Optimistic Lock version counter. |

**`nfc_card`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK | — |
| student_id | UUID | FK | References `student_profile.user_id`. |
| card_uuid | VARCHAR(100) | UNIQUE, NOT NULL | Hardware ID from NFC tag. |
| status | ENUM | Default `ACTIVE` | Values: `ACTIVE`, `BLOCKED`. |

**`transactions`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK | — |
| sender_wallet_id | UUID | FK, NULLABLE | Source wallet. |
| receiver_wallet_id | UUID | FK, NULLABLE | Destination wallet. |
| amount | DECIMAL(19,4) | NOT NULL | Transaction value. |
| type | ENUM | NOT NULL | Values: `TOPUP`, `PURCHASE`, `WITHDRAWAL`. |
| status | ENUM | NOT NULL | Values: `PENDING`, `SUCCESS`, `FAILED`. |
| idempotency_key | VARCHAR(255) | UNIQUE, NOT NULL | Prevents double-charging. |

### 2.4 Audit & Operations — Cross-Cutting

> **Purpose:** Dedicated tracking for non-financial state changes. Business tables are kept strictly clean and separated from audit data.

**`entity_audit_logs`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | UUID | PK | — |
| entity_name | VARCHAR(100) | NOT NULL | E.g., `wallet`, `nfc_card`, `users`. |
| entity_id | UUID | NOT NULL | The ID of the modified record. |
| old_state | JSONB | NULLABLE | The state before modification. |
| new_state | JSONB | NOT NULL | The state after modification. |
| changed_by | UUID | NULLABLE | User ID initiating the change. |
| changed_at | TIMESTAMP | Default NOW() | — |

### 2.5 Redis Keyspace — Ephemeral State

| Key Pattern | Value Structure | TTL | Description |
|---|---|---|---|
| `nfc_session:<token>` | `{"uuid", "merchantId", "amount"}` | 10s | Dynamically linked execution state. |
| `idem:api:<key>` | `{"status", "response_body"}` | 24h | Network retry API idempotency. **Fallback:** If this key is missing (expired), the API must query the PostgreSQL `transactions.idempotency_key` column before processing. If found in DB, return the stored response; otherwise treat as a new transaction. |
| `otp:<type>:<target>` | `{"otp_hash", "attempts"}` | 300s | Hashed OTPs to prevent extraction. |

---

## 3. API Specification

| Property | Value |
|---|---|
| Base URL | `/api/v1` |
| Auth Mechanism | Stateless JWT (Bearer token) |
| Style | RESTful — standard HTTP status codes |
| Pagination | All collection endpoints must enforce cursor-based or offset-based pagination to prevent DB degradation at scale. |

### 3.1 Authentication & Onboarding

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/register/student` | Register a new student account. |
| POST | `/auth/register/merchant` | Register a new merchant account. |
| POST | `/auth/verify-otp` | Validate Email OTP from Redis. Enables the user account. |
| POST | `/auth/verify-phone` | Validate SMS OTP from Redis. Fully activates account and creates empty wallet. |
| POST | `/auth/login` | Authenticate. Response: `{ access_token, refresh_token, user_role }`. |
| POST | `/auth/refresh` | Exchange a refresh token for a new access token. |
| POST | `/auth/logout` | Invalidate current session. |
| POST | `/auth/password/forgot` | Initiate the forgot password flow. |
| POST | `/auth/password/reset` | Verify OTP and set new password. |

### 3.2 Wallet Operations

| Method | Endpoint | Description |
|---|---|---|
| POST | `/wallet/pin` | Set or update the wallet transaction PIN. |
| GET | `/wallet/balance` | Retrieve the authenticated user's current wallet balance. |
| POST | `/wallet/unlock/initiate` | Initiate wallet unlock — sends SMS OTP. |
| POST | `/wallet/unlock/verify` | Submit OTP to unlock wallet; resets status to `ACTIVE` and `pin_attempts` to 0. |
| POST | `/wallet/topup` | Calls Razorpay Orders API. Logs a `PENDING` transaction. |
| POST | `/wallet/withdraw` | Validates PIN. Creates `PENDING` transaction. Calls RazorpayX Payouts API. |
| POST | `/wallet/pin/forgot` | Initiate forgot wallet PIN flow. |
| POST | `/wallet/pin/reset` | Verify OTP and set new wallet PIN. |

### 3.3 NFC & Payment Flow (Role-Specific)

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/payment/nfc/session` | Merchant | Body: `{ nfc_uuid, amount, device_id }`. Response: `{ nfcSessionToken, expiresIn: 10 }`. Creates a 10-second Redis session token. |
| POST | `/payment/lookup` | Merchant | Body: `{ nfc_uuid }`. Response: `{ valid: true, student_name, wallet_status }`. |
| POST | `/payment/process` | Merchant | Body: `{ nfcSessionToken, amount, pin, idempotency_key }`. Executes atomic Redis `GETDEL`. Verifies PIN and performs Optimistic Ledger update on the sender wallet. Uses Pessimistic Locking (`SELECT ... FOR UPDATE`) on `receiver_wallet_id` to prevent race conditions during peak-hour contention at campus shops. |

### 3.4 Utility Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/analytics/summary` | Auth Users | Aggregates transaction data for the authenticated user. Response: `{ total_spent (DECIMAL), txn_count (INTEGER) }`. |
| GET | `/documents/receipt/{txnId}` | Auth Users | Generates a PDF receipt for the transaction. Validates ownership. Response: PDF file stream with amount, timestamp, sender, receiver, status. |
| POST | `/admin/device/register` | Admin | Registers a specific POS terminal. Body: `{ merchant_id, device_fingerprint }`. Populates `merchant_profile.authorized_device_id`. |

---

## 4. Application Flows

### 4.1 Onboarding Flow

1. Student submits registration form. Argon2id-hashed password is stored.
2. Hashed Email OTP written to Redis. User submits OTP → account enabled.
3. SMS OTP sent to phone. User submits OTP → account fully activated, empty wallet created.
4. **Hardware Linking:** User logs in, taps NFC card on phone. App calls `/payment/nfc/link`.

### 4.2 Security Flow: Wrong PIN Lockout

| Step | Event | System Action | HTTP Response |
|---|---|---|---|
| 1–2 | Student enters wrong PIN | Backend increments `wallet.pin_attempts`. | 401 Unauthorized |
| 3 | Student enters wrong PIN (3rd time) | `pin_attempts → 3`. `wallet.status = LOCKED`. Trigger written to `entity_audit_logs`. | 403 Forbidden ("Wallet Locked") |
| Recovery | Student requests unlock via app | Calls `/wallet/unlock/initiate`. Backend sends SMS OTP. | 200 OK |
| Resolution | Student submits valid OTP | Calls `/wallet/unlock/verify`. Status reset to `ACTIVE`, attempts reset to 0. | 200 OK |

### 4.3 Security Flow: Lost / Stolen Card

1. **Event:** Student loses their NFC ID card.
2. **Action:** Student opens App → My Cards → toggles Block. API `/payment/nfc/status` sets `nfc_card.status = BLOCKED`. Trigger written to `entity_audit_logs`.
3. **Prevention:** Unauthorized user attempts purchase. Backend checks `nfc_card.status`. Returns 403 Forbidden ("Card Blocked"). Transaction aborted.

### 4.4 Token-Gated NFC Payment Flow

> **Core principle:** Decouples the physical NFC tap from financial authorization using ephemeral Redis tokens with cryptographic binding.

1. **Initiation** — Merchant enters the transaction amount. Phone UI prompts: "Tap Student ID Card".
2. **Session Creation** — Merchant taps card. App calls `POST /payment/nfc/session`. A 10-second Redis token is created and bound explicitly to UUID, Merchant ID, and Amount.
3. **Lookup** — App fetches student name to display a confirmation prompt (e.g., "Pay ₹50.00 from Pratham?").
4. **Authorization** — Student visually verifies the dynamically linked amount and enters their 6-digit PIN on the MPoC-secured Android UI.
5. **Execution** — App sends `POST /payment/process` with the Redis token and PIN.
6. **Atomicity** — Backend executes a thread-safe `GETDEL` on the Redis token. Validates amount matches exactly. Validates PIN against the Ledger Module. Returns success.

---

## 5. System Architecture, Resilience & Guidelines

### 5.1 Strict Inter-Module Communication Rules

> Developers must strictly adhere to these rules to prevent the architecture from degrading into a tightly coupled "spaghetti" system.

| Rule | Detail |
|---|---|
| No Cross-Schema Queries | Querying data from another module's database schema is strictly prohibited. It creates hard coupling at the database level. |
| Synchronous Access | If a module requires immediate data from another (e.g., Gateway needs User Profile from IAM), it must request it via a strict internal code interface — e.g., `IamInterface.getUser()`. |
| Asynchronous State Changes | State changes that do not require an immediate blocking response must be communicated via the RabbitMQ event bus. |

### 5.2 Event-Driven Webhook Ingestion — RabbitMQ

1. Razorpay fires `order.paid` or RazorpayX fires `payout.processed`.
2. Gateway verifies HMAC signature, acknowledges HTTP 200, and publishes the event to RabbitMQ.
3. Ledger Module asynchronously consumes the event, performs Optimistic Locking on the Wallet table, and updates balances.
4. **Dead Letter Queue (DLQ):** Failed messages are automatically routed to a DLQ for retries with exponential backoff.

### 5.3 Traceability & Distributed Monitoring

| Mechanism | Implementation |
|---|---|
| Correlation IDs | The API Gateway must generate a unique Correlation-ID (UUID) for every incoming HTTP request. |
| Propagation | This ID must be passed through all internal synchronous method calls, attached to all application logs, and embedded into the headers of all RabbitMQ event payloads. |
| Goal | Enable complete end-to-end distributed tracing of any transaction across module boundaries. |

### 5.4 Background Workers — Cron Jobs

**`ReconcilePendingTopupsTask` · Every 15 minutes**
- Queries the Ledger for transactions stuck in `PENDING` for > 30 minutes.
- Makes a synchronous GET call to Razorpay APIs to check actual payment status.
- Forces internal ledger updates to `SUCCESS` or `FAILED` to heal dropped webhooks.
- **Edge Case — Auto-Refund:** If a `PENDING` transaction is found to be `CAPTURED` at Razorpay but the corresponding User Wallet is `LOCKED` or `DELETED`, the worker must immediately trigger a Razorpay Refund API call to reverse the charge back to the source bank account.

**`OvernightLedgerReconciliationTask` · Daily at 02:00 AM**
- Ensures absolute mathematical integrity of the closed-loop system.
- Calculates total daily inflows against total daily outflows.
- Asserts the equation: **`Total In - Total Out = 0`**
- Any discrepancy > 0.00 immediately triggers a P1 alert to the Admin dashboard and halts all scheduled RazorpayX bulk settlements.

---

## 6. Development Phase Distribution

| Phase | Title | Goal | Key Backend Deliverables |
|---|---|---|---|
| 1 | Foundation | Establish architecture and zero-trust security baseline. | Spring Boot/Node.js with Bounded Contexts. PostgreSQL schemas via Flyway/Liquibase. Auth APIs with Argon2id, SHA-256 token hashing, Redis OTP flows. |
| 2 | Core Engine & Async Messaging | Inter-module communication and resilient Ledger. | Deploy RabbitMQ. Ledger Module with Optimistic Locking. Wallet operations (PIN, Balance, Unlock) and `entity_audit_logs` logic. |
| 3 | Gateway Integration | Secure external money movement. | Razorpay Orders (Top-up) and RazorpayX Payouts (Withdrawal). HMAC-verified Webhook listeners publishing to RabbitMQ exchange. |
| 4 | Transaction Engine | The core campus payment product. | High-speed Redis `nfc_session` logic. PSD2 dynamic linking architecture. Atomic `GETDEL` payment execution. Mitigates relay attacks and hardware cloning. |
| 5 | Automated Reconciliation & Utilities | Enterprise resilience and app connectivity. | Cron workers (`OvernightLedgerReconciliationTask`, `ReconcilePendingTopupsTask`). Paginated analytics & PDF receipt generation. Android app NFC integration. |

---

*Internal Developer Reference · PaymentX Platform*
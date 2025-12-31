# PaymentX – Technical Specification

## 1. Project Details

### 1.1 Project Overview

PaymentX is a closed-loop digital payment platform designed for campus environments. It facilitates cashless transactions between students and campus merchants using NFC-enabled identity cards linked to a secure digital wallet. The system mimics real-world banking architecture, prioritizing data integrity, transactional atomicity, and security compliance.

### 1.2 Problem Statement

Campus payments currently lack integration with university identification systems. PaymentX bridges this gap by associating physical student ID cards (NFC UUIDs) with a secure backend wallet, enabling seamless, offline-like payment experiences without relying on open banking networks.

### 1.3 Scope of v1

The initial release (v1) focuses on the core transactional engine, security framework, and essential user flows for students and merchants.

- **Identity Management**: Role-based access control (RBAC) with strict verification.
- **Financial Core**: Wallet management, PIN security, top-ups (Stripe Test Mode), and simulated withdrawals.
- **Payments**: NFC-based Student-to-Merchant payments with atomic consistency.
- **Security**: Multi-factor authentication (Email + SMS), self-service account recovery, and session management.

### 1.4 User Roles & Responsibilities

- **Student**: Registers with university email, links physical NFC card, manages wallet balance, and authorizes payments via PIN .
- **Merchant**: Registers business profile, initiates payment requests via NFC scanning, and withdraws accumulated funds .
- **Admin**: System oversight (scope limited to logging and monitoring in v1).

### 1.5 Assumptions & Constraints

- **Network**: All transactions require active internet connectivity (online-only processing).
- **Hardware**: Students possess ISO 14443 compliant NFC cards; Merchants possess NFC-enabled Android devices.
- **Currency**: System operates exclusively in INR (Indian Rupee).
- **Compliance**: PCI-DSS standards are adhered to by offloading sensitive card data handling to Stripe.

### 1.6 Non-Goals (Out of Scope for v1)

- Offline transaction queuing or store-and-forward mechanisms.
- Real-money banking settlements (Integration is limited to Test Mode).
- Public UPI or open banking interoperability.
- Regulatory compliance modules (KYC/AML).

---

## 2. Database Schema

**Database Engine**: PostgreSQL

**Justification**: Chosen for ACID compliance, robust transaction support, and row-level locking capabilities required for financial ledgers.

### 2.1 Identity & Access Management

#### Table: users

**Purpose**: Central identity store. Rows are created upon registration but remain effectively disabled until full verification.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | Unique User ID. |
| email | VARCHAR(255) | UNIQUE, NOT NULL, INDEX | College (@vit.ac.in) or Business email. |
| password_hash | VARCHAR(255) | NOT NULL | Bcrypt hash of login password. |
| phone | VARCHAR(20) | UNIQUE, NOT NULL, INDEX | E.164 Format (e.g., +919999999999). |
| role | ENUM | NOT NULL | Values: STUDENT, MERCHANT, ADMIN. |
| is_email_verified | BOOLEAN | Default FALSE | Step 1 verification status. |
| is_phone_verified | BOOLEAN | Default FALSE | Step 2 verification status. Account active only when TRUE. |
| created_at | TIMESTAMP | Default NOW() | |

#### Table: Session

**Purpose**: Manages persistent sessions and enforces "Single Active Session" security policy.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | BIGINT | PK | Auto-increment ID. |
| user_id | UUID | FK, INDEX | References users.id. |
| refresh_token | VARCHAR(255) | UNIQUE, INDEX | Secure, long-lived token string. |
| lastUsedAt | TIMESTAMP | Default NOW() | Audit timestamp, updated on token refresh. |
| revoked | BOOLEAN | Default FALSE | Soft-delete flag for session invalidation. |

#### Table: otp_verification

**Purpose**: Transient storage for OTPs with anti-spam throttling.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | |
| target | VARCHAR(255) | INDEX | Email Address or Phone Number. |
| otp_hash | VARCHAR(255) | NOT NULL | Argon2/Bcrypt hash of the OTP code. |
| type | ENUM | NOT NULL | Values: EMAIL, PHONE, UNLOCK_WALLET, PASSWORD_RESET, PIN_RESET. |
| expires_at | TIMESTAMP | NOT NULL | TTL (Time-To-Live). |
| is_used | BOOLEAN | Default FALSE | Replay attack prevention. |
| attempts | INTEGER | Default 0 | Rate limiting counter (Max 5). |

### 2.2 User Profiles

#### Table: student_profile

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| user_id | UUID | PK, FK | References users.id. |
| full_name | VARCHAR(100) | NOT NULL | |
| college_reg_no | VARCHAR(50) | UNIQUE, NOT NULL | University ID (e.g., "23BCE1001"). |
| date_of_birth | DATE | NOT NULL | |

#### Table: merchant_profile

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| user_id | UUID | PK, FK | References users.id. |
| business_name | VARCHAR(100) | NOT NULL |. |
| business_address| TEXT | NOT NULL | |
| gst_tax_id | VARCHAR(50) | NULLABLE | Optional compliance field. |

### 2.3 Financial Core

#### Table: wallet

**Purpose**: The central ledger entity. Implements Optimistic Locking to handle concurrency.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | |
| user_id | UUID | FK, UNIQUE | 1:1 mapping with User . |
| balance | DECIMAL(19,4) | Default 0.0000 | High-precision numeric type. |
| pin_hash | VARCHAR(255) | NULLABLE | Hashed Transaction PIN (distinct from login password). |
| status | ENUM | Default 'ACTIVE' | Values: ACTIVE, LOCKED (Security lockout). |
| pin_attempts | INTEGER | Default 0 | Tracks consecutive failed PIN entries (Max 3). |
| version | BIGINT | Default 0 | Optimistic Lock version counter. |

#### Table: nfc_card

**Purpose**: Links physical hardware to a student account with user-controlled blocking.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | |
| student_id | UUID | FK | References student_profile.user_id. |
| card_uuid | VARCHAR(100) | UNIQUE, NOT NULL | Hardware ID from NFC tag. |
| status | ENUM | Default 'ACTIVE' | Values: ACTIVE, BLOCKED (User toggled). |
| linked_at | TIMESTAMP | Default NOW() | |

### 2.4 Ledger & Audit

#### Table: transactions

**Purpose**: Immutable double-entry ledger for all fund movements.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | |
| sender_wallet_id| UUID | FK, NULLABLE | Source (Null for external Top-up). |
| receiver_wallet_id| UUID | FK, NULLABLE| Destination (Null for Withdrawal). |
| amount | DECIMAL(19,4) | NOT NULL | Transaction value. |
| type | ENUM | NOT NULL | Values: TOPUP, PURCHASE, WITHDRAWAL, REFUND. |
| status | ENUM | NOT NULL | Values: PENDING, SUCCESS, FAILED. |
| idempotency_key| VARCHAR(255) | UNIQUE | Prevents double-charging on network retries. |
| created_at | TIMESTAMP | Default NOW() | |

#### Table: payment_gateway_logs

**Purpose**: Audit trail for external Stripe webhooks.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| id | UUID | PK | |
| transaction_id | UUID | FK | References transactions.id. |
| gateway_ref_id | VARCHAR(255) | INDEX | Stripe PaymentIntent ID. |
| metadata | JSONB | NOT NULL | Full Webhook Payload. |

---

## 3. API Specification

**Design Principles**: RESTful architecture, stateless communication (JWT), standard HTTP status codes.

**Base URL**: `/api/v1`

### 3.1 Authentication & Onboarding

**Access**: Public

#### Register Student

**POST** `/auth/register/student`

- **Body**: `{ email, college_reg_no, password, phone, full_name, date_of_birth }`
- **Constraint**: Email must end in @vit.ac.in.
- **Response**: 200 OK (Triggers Email OTP).

#### Register Merchant

**POST** `/auth/register/merchant`

- **Body**: `{ email, business_name, password, phone, business_address, gst_id }`
- **Response**: 200 OK (Triggers Email OTP).

#### Verify OTP (Identity)

**POST** `/auth/verify-otp`

- **Body**: `{ email, otp }`
- **Action**: Validates Email OTP. Enables user (Email Verified). Triggers SMS OTP for phone.

#### Verify Phone (Security)

**POST** `/auth/verify-phone`

- **Body**: `{ phone, otp }`
- **Action**: Validates SMS OTP. Fully activates account. Creates empty wallet.

#### Login

**POST** `/auth/login`

- **Body**: `{ email, password }`
- **Action**: Invalidates all previous sessions for user. Issues new JWT Access Token + Refresh Token.
- **Response**: `{ access_token, refresh_token, user_role }`.

#### Refresh Session

**POST** `/auth/refresh`

- **Body**: `{ refresh_token }`
- **Action**: Rotates Refresh Token (One-time use). Returns new pair.

#### Logout

**POST** `/auth/logout`

- **Body**: `{ refresh_token }`
- **Action**: Marks session as revoked.

#### Forgot Password (Initiate)

**POST** `/auth/password/forgot`

- **Body**: `{ email }`
- **Action**: Validates email exists in system. Triggers PASSWORD_RESET OTP to registered email.
- **Response**: 200 OK (OTP sent to email).

#### Forgot Password (Verify & Reset)

**POST** `/auth/password/reset`

- **Body**: `{ email, otp, new_password }`
- **Action**: Validates PASSWORD_RESET OTP. Updates password_hash in users table. Invalidates all existing sessions.
- **Response**: 200 OK (Password reset successful).

### 3.2 Wallet Operations

**Access**: Authenticated Users

#### Set Transaction PIN

**POST** `/wallet/pin`

- **Body**: `{ pin }` (4-6 digits).
- **Action**: Securely hashes and stores PIN.

#### Get Balance

**GET** `/wallet/balance`

- **Response**: `{ "balance": 500.00, "currency": "INR", "status": "ACTIVE" }`
    - Note: Currency is hardcoded to "INR" in v1 (per Section 1.5).

#### Initiate Wallet Unlock

**POST** `/wallet/unlock/initiate`

- **Context**: Used when wallet.status is LOCKED due to 3 wrong PIN attempts.
- **Action**: Triggers UNLOCK_WALLET OTP via SMS.

#### Verify Wallet Unlock

**POST** `/wallet/unlock/verify`

- **Body**: `{ otp }`
- **Action**: Validates OTP. Resets wallet.status to ACTIVE and pin_attempts to 0.

#### Initiate Top-up

**POST** `/wallet/topup`

- **Body**: `{ amount }`
- **Response**: `{ client_secret, txn_id }` (Stripe PaymentIntent).

#### Withdraw Funds

**POST** `/wallet/withdraw`

- **Body**: `{ amount, pin }`
- **Action**: Atomic debit and external payout trigger.

#### Forgot Wallet PIN (Initiate)

**POST** `/wallet/pin/forgot`

- **Body**: `{ password }`
- **Action**: Validates account password against password_hash. Triggers PIN_RESET OTP via SMS to registered phone.
- **Response**: 200 OK (OTP sent to phone).

#### Forgot Wallet PIN (Verify & Reset)

**POST** `/wallet/pin/reset`

- **Body**: `{ password, otp, new_pin }`
- **Action**: Validates account password and PIN_RESET OTP. Updates pin_hash in wallet table. Resets pin_attempts to 0 and status to ACTIVE.
- **Response**: 200 OK (PIN reset successful).

### 3.3 NFC & Payment Flow

**Access**: Role-Specific

#### Link NFC Card (Student Only)

**POST** `/payment/nfc/link`

- **Body**: `{ nfc_uuid }`
- **Constraint**: UUID must be unique globally.

#### Toggle Card Status (Student Only)

**POST** `/payment/nfc/status`

- **Body**: `{ status }` (Values: ACTIVE, BLOCKED).
- **Purpose**: User-controlled freeze for lost cards.

#### Lookup Student Card (Merchant Only)

**POST** `/payment/lookup`

- **Body**: `{ nfc_uuid }`
- **Purpose**: Fetches student details for the confirmation screen after the tap but before PIN entry.
- **Backend Logic**:
    - Check if nfc_uuid exists and nfc_card.status is ACTIVE.
    - Check if linked wallet.status is ACTIVE.
    - Retrieve student_profile.full_name.
- **Response (Success)**:
  ```json
  {
    "valid": true,
    "student_name": "Pratham Khanduja",
    "wallet_status": "ACTIVE"
  }
  ```
- **Response (Error)**: 404 Not Found or 403 Card Blocked.

#### Process Payment (Merchant Only)

**POST** `/payment/process`

- **Body**:
  ```json
  {
    "nfc_uuid": "04:A3:...",
    "amount": 500.00,
    "pin": "123456",
    "idempotency_key": "unique_uuid_..."
  }
  ```
- **Action**: Atomic transfer from Student to Merchant.
- **Response (Success)**:
  ```json
  {
    "status": "SUCCESS",
    "txn_id": "uuid...",
    "amount": 500.00,
    "currency": "INR"
  }
  ```
- **Response (Error)**: Error details with appropriate message (e.g., "Wrong PIN", "Insufficient Balance", "Card Blocked").

### 3.4 Operations

**Access**: Public (Signature Verified)

#### Stripe Webhook

**POST** `/webhooks/stripe`

- **Body**: Raw Stripe Event.
- **Action**: Confirms Top-up transactions upon payment_intent.succeeded.

### 3.5 Admin & Utility

#### Transaction Analytics

**GET** `/analytics/summary`

- **Access**: Authenticated Users
- **Action**: Aggregates transaction data for the authenticated user.
- **Response**: `{ total_spent, txn_count }`
    - `total_spent`: Total amount spent/received (DECIMAL)
    - `txn_count`: Number of transactions (INTEGER)

#### Transaction Receipt

**GET** `/documents/receipt/{txnId}`

- **Access**: Authenticated Users
- **Action**: Generates PDF receipt for the specified transaction. Validates that the transaction belongs to the authenticated user.
- **Response**: PDF File Stream with transaction details (amount, timestamp, sender, receiver, status).

#### System Configuration

**GET** `/config/public`

- **Access**: Public
- **Action**: Returns public system configuration required for client initialization.
- **Response**: `{ stripe_key }`
    - `stripe_key`: Stripe publishable key (e.g., "pk_test_...")

---

## 4. Application Flow

### 4.1 Onboarding Flow (Student)

1. **Registration**: User POSTs details to `/auth/register/student`. Backend validates @vit.ac.in domain and creates a DISABLED user record. Email OTP is sent.
2. **Email Verification**: User POSTs OTP to `/auth/verify-otp`. Backend verifies hash, marks is_email_verified=TRUE, and triggers Twilio SMS OTP.
3. **Phone Verification**: User POSTs SMS OTP to `/auth/verify-phone`. Backend marks is_phone_verified=TRUE and creates a wallet (Status: ACTIVE, Balance: 0).
4. **Hardware Linking**: User logs in, taps card on phone. App calls `/payment/nfc/link`. Backend associates UUID with Student Profile.

### 4.2 Security Flow: Wrong PIN Lockout

1. **Attempt 1 & 2**: Merchant initiates payment. Student enters wrong PIN. Backend increments wallet.pin_attempts. Returns 401 Unauthorized.
2. **Attempt 3**: Student enters wrong PIN. Backend increments pin_attempts to 3. Sets wallet.status = LOCKED. Returns 403 Forbidden ("Wallet Locked").
3. **Recovery**: Student opens App. UI detects LOCKED status. Student requests unlock via `/wallet/unlock/initiate`.
4. **Resolution**: Backend sends SMS. Student submits OTP to `/wallet/unlock/verify`. Backend resets status to ACTIVE and attempts to 0.

### 4.3 Security Flow: Lost Card

1. **Event**: Student loses ID card.
2. **Action**: Student opens App -> My Cards -> Toggles "Block". API `/payment/nfc/status` sets nfc_card.status = BLOCKED.
3. **Prevention**: Unauthorized user attempts purchase. Backend checks nfc_card.status. Returns 403 Forbidden ("Card Blocked"). Transaction aborted.

### 4.4 Payment Flow (Handover & Verify)

This flow ensures the student explicitly authorizes the payment on the merchant's device.

1. **Initiation (Merchant)**: Merchant opens the App. Enters Amount (e.g., ₹50.00). Phone prompts: "Tap Student ID Card".
2. **Handover & Tap**: Merchant hands phone to Student (or Student reaches out). Student taps their NFC ID Card on the Merchant's phone.
3. **Lookup (System)**: Merchant App sends nfc_uuid to `POST /payment/lookup`. Backend checks nfc_card.status and wallet.status. Returns `{ "valid": true, "student_name": "Pratham Khanduja", "wallet_status": "ACTIVE" }`.
4. **Confirmation Screen**: App displays: "Pay ₹50.00 from Pratham?". Input field appears: "Enter Wallet PIN".
5. **Authorization (Student)**: Student checks the amount and their name. Student enters 6-digit PIN on the Merchant's phone. Student clicks "PAY".
6. **Execution (Backend)**: Merchant App sends `{ nfc_uuid, amount, pin, idempotency_key }` to `POST /payment/process`. Backend validates PIN. Atomic Transaction: Debits Student wallet, Credits Merchant wallet, inserts transaction record with status='SUCCESS'.
7. **Completion**: App shows Green Success Tick. Merchant takes phone back.

### 4.5 Account Recovery Flow: Forgot Password

1. **Initiation**: User navigates to "Forgot Password" in App/Web. Enters registered email. App POSTs to `/auth/password/forgot`.
2. **Validation**: Backend checks if email exists in users table. If found, creates PASSWORD_RESET OTP entry in otp_verification table with type='PASSWORD_RESET'.
3. **OTP Delivery**: Backend sends 6-digit OTP to user's email via email service.
4. **Reset Request**: User enters OTP and new password. App POSTs to `/auth/password/reset`.
5. **Verification**: Backend validates OTP from otp_verification table. Checks is_used=FALSE, expires_at > NOW(), attempts < 5.
6. **Password Update**: Backend hashes new password using Bcrypt. Updates users.password_hash. Marks OTP as is_used=TRUE.
7. **Session Invalidation**: Backend marks all Session records with matching user_id as revoked=TRUE (force re-login).
8. **Response**: Returns 200 OK. User must login again with new password.

### 4.6 Account Recovery Flow: Forgot Wallet PIN

1. **Initiation**: User navigates to "Forgot PIN" in App. Enters account password. App POSTs to `/wallet/pin/forgot`.
2. **Password Verification**: Backend validates password against users.password_hash. If incorrect, returns 401 Unauthorized.
3. **OTP Generation**: If password correct, backend creates PIN_RESET OTP entry in otp_verification table with type='PIN_RESET', target=user's phone.
4. **OTP Delivery**: Backend sends 6-digit OTP to user's registered phone via Twilio SMS.
5. **Reset Request**: User enters account password (again), OTP, and new PIN. App POSTs to `/wallet/pin/reset`.
6. **Dual Verification**: Backend validates:
    - Account password against users.password_hash.
    - PIN_RESET OTP from otp_verification table (is_used=FALSE, expires_at > NOW(), attempts < 5).
7. **PIN Update**: Backend hashes new PIN. Updates wallet.pin_hash, sets wallet.pin_attempts=0, wallet.status='ACTIVE'.
8. **OTP Cleanup**: Marks OTP as is_used=TRUE.
9. **Response**: Returns 200 OK. User can now use new PIN for transactions.

---

## 5. Development Phase Distribution

### Phase 1: Foundation (Identity & Security)

**Goal**: Secure User Management and Session handling.

- **Backend**: Setup Spring Boot, PostgreSQL, Twilio. Implement Auth APIs (Register, Verify, Login, Refresh).
- **Database**: Implement users, Session, otp_verification tables.
- **Deliverable**: Users can register via Email/SMS flows and maintain a persistent session.

### Phase 2: Financial Core (Wallet & Money)

**Goal**: Wallet state management and Money Load.

- **Backend**: Implement wallet logic with Optimistic Locking. Integrate Stripe Java SDK for Top-ups. Build Webhook listener.
- **Security**: Implement PIN Hashing and "Wrong PIN" counter logic.
- **Deliverable**: Users can Set PIN, Add Money (Simulated), and View Balance.

### Phase 3: Transaction Engine (NFC)

**Goal**: Core Payment Product.

- **Backend**:
    - Implement nfc_card linking.
    - Implement `POST /payment/lookup` (Read-only verification).
    - Refine `POST /payment/process` to handle the atomic debit/credit.
    - Implement Idempotency.
- **Security**: Implement Card Blocking/Unblocking logic.
- **Deliverable**: Smooth "Tap -> Confirm Name -> Enter PIN -> Success" flow.

### Phase 4: Self-Service Recovery & Polish

**Goal**: User autonomy and robustness.

- **Backend**: Implement Wallet Unlock APIs (OTP flow). Generate Transaction Receipts.
- **Frontend**: Integrate Stripe Payment Sheet. Handle "Locked" and "Blocked" UI states.
- **Deliverable**: Production-ready system with recovery flows.

### Phase 5: Client Integration

**Goal**: Mobile App Connection.

- **Mobile**: Connect Android/React App to Backend. Implement native NFC scanning.
- **Testing**: End-to-end flow verification (Onboarding -> Top-up -> Payment -> Withdrawal).
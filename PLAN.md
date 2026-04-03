# Project Plan: YNAB Amazon Automator

**Version:** 1.0.0  
**Status:** Draft  
**Target Hardware:** Raspberry Pi 3 (or equivalent low-resource environment)  
**Stack:** Kotlin, Spring Boot 3, React, SQLite, Flyway, Docker  

---

## 1. Overview

### Elevator Pitch
Automate YNAB transaction categorization by parsing Amazon order confirmation emails. The system extracts order information, classifies the order based on user-supplied category descriptions using AI, and updates YNAB transactions accordingly.

### Core Constraints
- **Hardware:** Must run on Raspberry Pi 3 (ARMv7/ARM64, limited RAM).
- **Memory:** JVM heap limited to 512MB.
- **Architecture:** Layered Architecture (Controller -> Service -> Infrastructure). No Hexagonal ports/adapters.
- **Database:** SQLite with Flyway migrations.
- **Tenancy:** Single tenant v1, structured for multi-tenancy v2 (no global static state).
- **Security:** API keys stored in DB (plaintext v1), UI not exposed to internet.
- **Error Handling:** Failed email parses are discarded/logged. Pending transactions remain indefinitely until matched.

---

## 2. Architecture

### Component Diagram

```mermaid
graph TD
    User[User Browser] -->|HTTPS| SpringBoot[Spring Boot App]
    
    subgraph "Docker Container (Pi 3)"
        SpringBoot -->|Serves| React[React SPA]
        SpringBoot -->|Reads/Writes| SQLite[(SQLite DB)]
        
        subgraph "Application Layers"
            Web[Web Layer<br/>Controllers]
            Svc[Service Layer<br/>Business Logic]
            Infra[Infrastructure Layer<br/>External API Clients]
        end
        
        SpringBoot --> Web
        Web --> Svc
        Svc --> Infra
        Svc --> SQLite
    end
    
    subgraph "External World"
        FastMail[FastMail JMAP]
        Gemini[Google Gemini]
        YNAB[YNAB API]
    end
    
    Infra -->|RestClient| FastMail
    Infra -->|RestClient| Gemini
    Infra -->|RestClient| YNAB
```

### Project Structure

```text
src/main/kotlin/com/ynabauto
├── Application.kt
├── config
│   ├── SchedulerConfig.kt      // @EnableScheduling
│   ├── WebConfig.kt            // SPA Fallback mapping
│   └── RestClientConfig.kt     // Global RestClient customization
├── web
│   ├── ConfigController.kt     // API Keys & Category Config
│   ├── OrderController.kt      // Pending Orders View
│   ├── LogController.kt        // Sync Logs View
│   └── dto                     // Request/Response objects
├── service
│   ├── EmailIngestionService.kt// Polls Email, parses, saves Orders
│   ├── YnabSyncService.kt      // Polls YNAB, matches Transactions
│   ├── ClassificationService.kt// Orchestrates AI calls
│   ├── ConfigService.kt        // Manages App Config (Keys, Rules)
│   └── MatchingStrategy.kt     // Logic to match Order <-> Transaction
├── domain
│   ├── Order.kt                // JPA Entity
│   ├── SyncLog.kt              // JPA Entity
│   ├── CategoryRule.kt         // JPA Entity
│   └── AppConfig.kt            // JPA Entity (Key/Value)
├── infrastructure
│   ├── email
│   │   ├── EmailProviderClient.kt // Interface
│   │   └── FastMailJmapClient.kt  // Implementation (v1)
│   ├── ai
│   │   ├── ClassificationProvider.kt // Interface
│   │   └── GeminiProvider.kt   // Implementation (v1)
│   ├── ynab
│   │   ├── YnabClient.kt       // Interface
│   │   └── YnabRestClient.kt   // Implementation (v1)
│   └── persistence
│       └── Repositories.kt     // Spring Data JPA Interfaces
└── resources
    ├── static                  // Built React App
    └── db
        └── migration           // Flyway SQL scripts
```

---

## 3. Data Model (SQLite)

Managed via **Flyway**. All tables designed to allow future `tenant_id` addition.

### `app_config`
Stores runtime configuration and API keys.
| Column | Type | Notes |
| :--- | :--- | :--- |
| `key` | VARCHAR | PK (e.g., `YNAB_TOKEN`, `FASTMAIL_USER`) |
| `value` | TEXT | |
| `updated_at` | TIMESTAMP | |

### `category_rules`
Stores YNAB categories and user-supplied AI hints.
| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | INTEGER | PK |
| `ynab_category_id` | VARCHAR | |
| `ynab_category_name` | VARCHAR | |
| `user_description` | TEXT | Keywords/description for AI |
| `updated_at` | TIMESTAMP | |

### `amazon_orders`
Parsed email data waiting for transaction match.
| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | INTEGER | PK |
| `email_message_id` | VARCHAR | Unique |
| `order_date` | TIMESTAMP | |
| `total_amount` | DECIMAL | |
| `items_json` | TEXT | Array of item names |
| `status` | VARCHAR | `PENDING`, `MATCHED`, `COMPLETED`, `DISCARDED` |
| `ynab_transaction_id` | VARCHAR | Nullable |
| `ynab_category_id` | VARCHAR | Nullable |
| `created_at` | TIMESTAMP | |

### `sync_logs`
Audit trail for UI.
| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | INTEGER | PK |
| `source` | VARCHAR | `EMAIL`, `YNAB` |
| `last_run` | TIMESTAMP | |
| `status` | VARCHAR | `SUCCESS`, `FAIL` |
| `message` | TEXT | Error details |

---

## 4. External API Specifications

### A. Email Provider (FastMail via JMAP)
**Interface:** `infrastructure.email.EmailProviderClient`  
**Client:** Spring `RestClient`  
**Required JMAP Methods:**
1.  `Mailbox/get`: Retrieve Inbox ID.
2.  `Email/query`: Search messages (`from` amazon.com, `subject` Order Confirmation, `receivedAt` > last_sync).
3.  `Email/get`: Fetch headers (Date, Message-ID).
4.  `EmailBody/get`: Fetch plain text/html body for parsing.

### B. YNAB API
**Interface:** `infrastructure.ynab.YnabClient`  
**Client:** Spring `RestClient`  
**Required Endpoints:**
1.  `GET /budgets/{budget_id}/transactions`: Fetch recent transactions for matching.
2.  `GET /budgets/{budget_id}/categories`: Populate frontend configuration.
3.  `PUT /budgets/{budget_id}/transactions/{transaction_id}`: Update `memo` and `category_id`.

### C. AI Classification (Google Gemini)
**Interface:** `infrastructure.ai.ClassificationProvider`  
**Client:** Spring `RestClient`  
**Required Endpoint:**
1.  `POST /generateContent`: Send item list + category rules, receive selected category.

---

## 5. Frontend Requirements

The React app will be served statically by Spring. It requires 4 specific views:

1.  **Configuration (API Keys)**
    *   Inputs: YNAB Token, FastMail User, FastMail Pass/Token, Budget ID, Gemini Key.
    *   Endpoint: `GET/PUT /api/config/keys`.
2.  **Category Descriptions**
    *   View: List of YNAB categories (fetched via backend proxy).
    *   Action: Text area next to each category for user-supplied AI hints.
    *   Endpoint: `GET /api/ynab/categories`, `PUT /api/config/categories`.
3.  **Pending Orders**
    *   View: Table of orders parsed from email not yet matched to YNAB.
    *   Endpoint: `GET /api/orders/pending`.
4.  **System Logs**
    *   View: List of sync attempts (Email/YNAB) with status/time.
    *   Endpoint: `GET /api/logs`.

---

## 6. Core Logic Flows

### Email Ingestion (`@Scheduled`)
1.  Load `FASTMAIL` creds from `app_config`.
2.  Call `EmailProviderClient.searchOrders(sinceDate)`.
3.  Parse Body (Amount, Date, Items).
    *   *Fail:* Log warning, skip.
    *   *Success:* Insert `amazon_orders` (Status: `PENDING`).
4.  Update `sync_logs` (Source: `EMAIL`).

### YNAB Sync & Match (`@Scheduled`)
1.  Load `YNAB` creds from `app_config`.
2.  Fetch `amazon_orders` where `status = 'PENDING'`.
3.  Fetch YNAB Transactions (recent).
4.  **Match:** Compare Amount/Date. If match -> Update `amazon_orders` (Status: `MATCHED`, set `ynab_transaction_id`).
5.  **Classify:** For `MATCHED` orders without category:
    *   Load `category_rules`.
    *   Call `ClassificationProvider`.
    *   Update `amazon_orders` with `category_id`.
    *   Call `YnabClient.updateTransaction`.
    *   Update `amazon_orders` (Status: `COMPLETED`).
6.  Update `sync_logs` (Source: `YNAB`).

---

## 7. Implementation Plan

### Phase 1: Foundation
- [ ] Initialize Spring Boot + Kotlin + Flyway + SQLite.
- [ ] Configure `application.properties` for SQLite and Flyway.
- [ ] Create `V1__init.sql` with table schemas.
- [ ] Create JPA Entities and Repositories.

### Phase 2: Infrastructure Clients
- [ ] Implement `RestClientConfig`.
- [ ] Implement `YnabRestClient` (Interface + Impl).
- [ ] Implement `FastMailJmapClient` (Interface + Impl).
- [ ] Implement `GeminiProvider` (Interface + Impl).

### Phase 3: Backend Logic
- [ ] Implement `ConfigService` (DB backed config).
- [ ] Implement `EmailIngestionService` (Scheduler + Parse logic).
- [ ] Implement `YnabSyncService` (Scheduler + Match logic).
- [ ] Implement `ClassificationService`.
- [ ] Create REST Controllers for Frontend features.

### Phase 4: Frontend
- [ ] Initialize React App.
- [ ] Build Configuration View (Keys).
- [ ] Build Category Rules View.
- [ ] Build Pending Orders View.
- [ ] Build Logs View.
- [ ] Configure Spring to serve static React build.

### Phase 5: Devops
- [ ] Setup e2e test to test the full workflow. Use a wiremock server to mock external APIs. Refer to the API references below.
- [ ] Setup GitHub Actions for CI/CD. Run tests automatically on any push

### Phase 6: Deployment
- [x] Create Multi-stage Dockerfile (Node build -> Gradle build -> JRE run).
- [ ] Test locally (x86) and on Target (ARM).
- [x] Setup automatic github release when merged to the prod branch

### Phase 7: Scope Creep
- [ ] TBD

---

## 8. Deployment & Resource Constraints

### Dockerfile Strategy
Multi-stage build to keep final image small.
1.  **Stage 1 (Node):** Build React app.
2.  **Stage 2 (Gradle):** Build Kotlin Jar, copy React build to `src/main/resources/static`.
3.  **Stage 3 (JRE):** Alpine Linux, copy Jar, set ENV.

### JVM Tuning (Pi 3)
Critical to prevent OOM kills.
```dockerfile
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"
```

### Database Persistence
Mount a volume to preserve SQLite data across container updates.
```bash
docker run -v /opt/ynab-auto/data:/app/data ...
```
*Note: Configure Spring `spring.datasource.url` to point to `/app/data/database.sqlite`.*

### Multi-Tenancy Future Proofing
- **No Static State:** All config read from DB per execution.
- **Schema:** Tables designed to accept `tenant_id` column later without logic refactors.
- **Auth:** v1 has no auth (UI not exposed). v2 will add auth middleware that sets tenant context.

---

## 9. Risks & Mitigations

| Risk | Mitigation |
| :--- | :--- |
| **Pi 3 Memory OOM** | Strict JVM heap limits (`-Xmx512m`), Serial GC, limit DB connection pool to 5. |
| **JMAP Complexity** | Use Spring `RestClient` for raw JSON over JMAP instead of heavy libraries. |
| **AI Cost/Latency** | Only classify when match is found. Cache category rules. |
| **YNAB Rate Limits** | Schedule syncs no faster than 5 minutes. Batch updates if possible (v1 uses single updates). |
| **Email Parsing Failures** | Catch exceptions in ingestion loop, log to `sync_logs`, continue processing next email. |

___

## API REFERENCES

Based on the YNAB v1 API OpenAPI specification, here are example responses for the requested endpoints:

## GET /budgets/{budgetId}/transactions

**Response Schema**: `TransactionsResponse`

```json
{
  "data": {
    "transactions": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "date": "2026-04-02",
        "amount": -25000,
        "memo": "Grocery shopping",
        "cleared": "cleared",
        "approved": true,
        "flag_color": null,
        "account_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "payee_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "category_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "transfer_account_id": null,
        "transfer_transaction_id": null,
        "matched_transaction_id": null,
        "import_id": null,
        "deleted": false
      }
    ],
    "server_knowledge": 1234567890
  }
}
```

## GET /budgets/{budgetId}/categories

**Response Schema**: `CategoriesResponse`

```json
{
  "data": {
    "category_groups": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "name": "Monthly Bills",
        "hidden": false,
        "deleted": false,
        "categories": [
          {
            "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "category_group_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "name": "Rent/Mortgage",
            "hidden": false,
            "original_category_group_id": null,
            "note": null,
            "budgeted": 150000,
            "activity": -150000,
            "balance": 0,
            "goal_type": null,
            "goal_creation_month": null,
            "goal_target": null,
            "goal_target_month": null,
            "goal_percentage_complete": null,
            "goal_months_to_budget": null,
            "goal_under_funded": null,
            "goal_overall_funded": null,
            "goal_overall_left": null,
            "deleted": false
          }
        ]
      }
    ],
    "server_knowledge": 1234567890
  }
}
```

## PUT /budgets/{budgetId}/transactions/{transactionId}

**Request Body Schema**: `PutTransactionWrapper`
**Response Schema**: `TransactionResponse`

```json
{
  "data": {
    "transaction": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "date": "2026-04-02",
      "amount": -30000,
      "memo": "Updated grocery shopping",
      "cleared": "cleared",
      "approved": true,
      "flag_color": "red",
      "account_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "payee_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "category_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "transfer_account_id": null,
      "transfer_transaction_id": null,
      "matched_transaction_id": null,
      "import_id": null,
      "deleted": false
    },
    "server_knowledge": 1234567891
  }
}
```

**Key Notes**:
- Amounts are in milliunits (e.g., $250.00 = 250000)
- All responses include `server_knowledge` for delta sync
- Categories include current month amounts (budgeted, activity, balance)
- Transactions can have various states: cleared, uncleared, reconciled
- UUIDs are used for all entity IDs [^1]

[^1]: [ynab-sdk-python/open_api_spec.yaml at main · ynab/ynab-sdk-python · GitHub](https://github.com/ynab/ynab-sdk-python/blob/main/open_api_spec.yaml) (100%)


Based on the JMAP specifications, here are example responses for the Fastmail JMAP API endpoints you requested:

## GET /.well-known/jmap

This endpoint returns a JMAP Session resource. While I couldn't find a complete Fastmail-specific example, the response follows RFC 8620 format [^2]:

```json
{
  "capabilities": {
    "urn:ietf:params:jmap:core": {
      "maxSizeUpload": 50000000,
      "maxConcurrentRequests": 10,
      "maxSizeRequest": 10000000,
      "maxCallsInRequest": 16,
      "maxObjectsInGet": 500,
      "maxObjectsInSet": 500,
      "collationAlgorithms": ["i;ascii-numeric", "i;ascii-casemap"]
    },
    "urn:ietf:params:jmap:mail": {},
    "urn:ietf:params:jmap:submission": {}
  },
  "accounts": {
    "ue150411c": {
      "name": "user@fastmail.com",
      "isPersonal": true,
      "isReadOnly": false,
      "accountCapabilities": {
        "urn:ietf:params:jmap:mail": {},
        "urn:ietf:params:jmap:submission": {}
      }
    }
  },
  "primaryAccounts": {
    "urn:ietf:params:jmap:mail": "ue150411c",
    "urn:ietf:params:jmap:submission": "ue150411c"
  },
  "username": "user@fastmail.com",
  "apiUrl": "https://api.fastmail.com/jmap/api/",
  "downloadUrl": "https://api.fastmail.com/jmap/download/{accountId}/{blobId}/{name}",
  "uploadUrl": "https://api.fastmail.com/jmap/upload/{accountId}/",
  "eventSourceUrl": "https://api.fastmail.com/jmap/eventsource/?types={types}&closeafter={closeafter}&ping={ping}"
}
```

## POST Email/query Response

For your Email/query request with filtering by from, subject, and date, the response would be [^1]:

```json
[[ "Email/query", {
  "accountId": "ue150411c",
  "queryState": "09aa9a075588-780599:0",
  "canCalculateChanges": true,
  "position": 0,
  "total": 115,
  "ids": [ 
    "Ma783e5cdf5f2deffbc97930a",
    "M9bd17497e2a99cb345fc1d0a",
    "Mc2781d5e856a908d8a35a564"
  ]
}, "a" ]]
```

## POST Email/get Response

For your Email/get request fetching email content, the response would be [^1]:

```json
[[ "Email/get", {
  "accountId": "abc",
  "state": "41234123231",
  "list": [
    {
      "id": "f123u457",
      "messageId": "<CAEeYn8h+Ld8=T4fP@mail.gmail.com>",
      "receivedAt": "2013-10-13T14:12:00Z",
      "bodyValues": {
        "1": {
          "isEncodingProblem": false,
          "isTruncated": true,
          "value": "<html><body><p>Hello world!</p></body></html>"
        },
        "2": {
          "isEncodingProblem": false,
          "isTruncated": false,
          "value": "Hello world!\n\n-- Sent by your friendly mailing list"
        }
      },
      "textBody": [
        {
          "partId": "2",
          "blobId": "B319437193",
          "size": 10343,
          "type": "text/plain"
        }
      ]
    }
  ],
  "notFound": []
}, "a" ]]
```

**Key Points:**
- All responses are wrapped in arrays with the method name, response object, and request ID
- The `accountId` in responses matches your request
- `bodyValues` contains the actual email content keyed by part ID
- `textBody` references the plain text parts of the email
- `notFound` array lists any requested IDs that couldn't be retrieved

[^1]: [RFC 8621: The JSON Meta Application Protocol (JMAP) for Mail](https://datatracker.ietf.org/doc/html/rfc8621) (54%)
[^2]: [RFC 8620: The JSON Meta Application Protocol (JMAP)](https://www.rfc-editor.org/rfc/rfc8620) (46%)

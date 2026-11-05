# Saga Orchestration Demo

A demo of the **orchestration-based Saga pattern** built with Spring Boot microservices that communicate asynchronously over Apache Kafka.

A central service, `store-orchestrator-service`, drives a purchase across three business services (product, order, payment). Each step is a command sent to a service, and each service replies with an event. When a step fails, the orchestrator issues **compensating actions** to undo the steps that already succeeded (restoring stock, cancelling the order).

The project is a learning and demo codebase. It is not production-ready. See [Known limitations](#known-limitations).

---

## Table of contents

- [Architecture](#architecture)
- [Modules](#modules)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [REST API](#rest-api)
- [Kafka topics](#kafka-topics)
- [Messages (DTO module)](#messages-dto-module)
- [Saga flow](#saga-flow)
- [Order and product state](#order-and-product-state)
- [Data model and seed data](#data-model-and-seed-data)
- [Configuration reference](#configuration-reference)
- [Running locally](#running-locally)
- [Running the tests](#running-the-tests)
- [Trying it out](#trying-it-out)
- [Build notes](#build-notes)
- [Known limitations](#known-limitations)

---

## Architecture

```
                         ┌──────────────────────────────┐
  HTTP POST /store/buy   │   store-orchestrator-service │
 ───────────────────────►│   :8080  (saga coordinator)  │
                         └──┬───────────────▲───────────┘
            *-actions topics│               │*-events topics
   ┌────────────────────────┼───────────────┼────────────────────────┐
   │                        ▼               │                        │
   │  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐    │
   │  │ product-service  │  │ order-service│  │ payment-service  │    │
   │  │ :8081  (stock)   │  │ :8082 (order)│  │ :8083  (payment) │    │
   │  │ H2 "product"     │  │ H2 "order"   │  │ H2 "payment"     │    │
   │  └──────────────────┘  └──────────────┘  └──────────────────┘    │
   └────────────────────────────────────────────────────────────────┘
                         Apache Kafka (3 brokers)
```

Key design points:

- **No direct HTTP calls between services.** Every interaction goes through Kafka.
- **Each service owns its data.** Each business service has its own in-memory H2 database and never reads another service's tables.
- **Central orchestration.** Only the orchestrator decides what happens next. Business services react to commands and report results. They do not know about each other.
- **Compensation instead of distributed transactions.** A failed step is undone by a compensating command, for example `ProductRollbackAction` restores stock.

---

## Modules

The project is a Maven multi-module build. The parent POM is `pom.xml` at the root.

| Module | Port | Role | Persistence | REST | Kafka |
|---|---|---|---|---|---|
| `dto` | – | Shared message contracts (actions and events). Library, no Spring | – | – | – |
| `store-orchestrator-service` | **8080** | Entry point and saga coordinator. Creates the Kafka topics | H2 on classpath, no entities | `POST /store/buy` | Produces actions, consumes events |
| `product-service` | **8081** | Product catalogue and stock | H2 `jdbc:h2:mem:product` | Only the H2 console | Consumes `product-actions`, produces `product-events` |
| `order-service` | **8082** | Order lifecycle (`NEW`, `PAID`, `CANCELLED`) | H2 `jdbc:h2:mem:order` | Only the H2 console | Consumes `order-actions`, produces `order-events` |
| `payment-service` | **8083** | Simulated payment processing | H2 `jdbc:h2:mem:payment` | Only the H2 console | Consumes `payment-actions`, produces `payment-events` |

Ports come from each module's `application.properties` (`server.port`).

---

## Project structure

```
saga-orchestration-demo/
├── pom.xml                              # parent POM: Spring Boot parent, Lombok, module list
├── dto/                                 # shared Kafka messages (library)
│   └── src/main/java/com/saga/dto/
│       ├── product/                     # ProductReserveAction, ProductReservedEvent, ...
│       ├── order/                       # CreateOrderAction, OrderCreatedEvent, ...
│       └── payment/                     # ProcessPaymentAction, PaymentProcessedEvent, ...
├── store-orchestrator-service/
│   └── src/main/java/
│       ├── com/saga/orchestrator/
│       │   ├── SagaOrchestratorServiceApplication.java
│       │   ├── config/KafkaConfiguration.java    # KafkaTemplate + topic creation
│       │   ├── controller/StoreController.java   # POST /store/buy
│       │   ├── dto/BuySomethingRequest.java      # REST request body
│       │   └── handler/KafkaEventHandler.java    # saga state transitions
│       └── util/CreditCardUtils.java             # random card number generator
├── product-service/
│   └── src/main/java/com/saga/product/
│       ├── dao/ProductRepository.java
│       ├── entity/ProductEntity.java
│       ├── kafka/ProductHandler.java             # reserve and rollback stock
│       └── service/ (ProductService, impl/ProductServiceImpl)
├── order-service/
│   └── src/main/java/com/saga/order/
│       ├── config/KafkaConfiguration.java
│       ├── dao/OrderRepository.java
│       ├── entity/ (OrderEntity, OrderStatus)
│       ├── kafka/OrderHandler.java               # create, cancel, mark as paid
│       └── service/ (OrderService, OrderServiceImpl)
└── payment-service/
    └── src/main/java/com/saga/payment/
        ├── dao/PaymentRepository.java
        ├── entity/PaymentEntity.java
        ├── kafka/PaymentHandler.java             # process payment
        └── service/ (PaymentService, impl/PaymentServiceImpl)
```

Each business service has the same layering: `kafka` (listener) → `service` → `dao` (Spring Data repository) → `entity`.

---

## Tech stack

- Java 27 (`java.version`), compiled with `maven.compiler.release` 25
- Spring Boot 3.4.4 (parent POM)
- Spring Web, Spring Data JPA, Spring for Apache Kafka
- H2 in-memory database
- Lombok 1.18.48
- JUnit 6.1.3 and Mockito 5.24.0 for unit tests (pinned in the parent POM)
- Maven (multi-module)
- google-java-format for code style

---

## REST API

Only the orchestrator exposes an API. The other services expose their H2 consoles only.

### `POST /store/buy`

Starts a purchase saga for one product.

- **Base URL:** `http://localhost:8080`
- **Content-Type:** `application/json`

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `productId` | UUID string | yes | ID of the product to buy |

**Responses**

| Status | When | Body |
|---|---|---|
| `200 OK` | The `ProductReserveAction` was handed to Kafka | empty |
| `400 Bad Request` | Body is not valid JSON or `productId` is not a UUID (Spring's default handling) | – |

**Important:** `200 OK` only means the saga was *started*. The controller sends the message and returns without waiting for the result, and it does not check whether the Kafka send succeeded. Watch the logs of each service to follow the outcome.

**Example**

```bash
curl -i -X POST http://localhost:8080/store/buy \
  -H 'Content-Type: application/json' \
  -d '{"productId": "550e8400-e29b-41d4-a716-446655440000"}'
```

```
HTTP/1.1 200
```

---

## Kafka topics

Each business service has one **actions** topic (commands from the orchestrator) and one **events** topic (replies to the orchestrator).

| Topic | Producer | Consumer | Carries |
|---|---|---|---|
| `product-actions` | orchestrator | product-service | `ProductReserveAction`, `ProductRollbackAction` |
| `product-events` | product-service | orchestrator | `ProductReservedEvent`, `ProductReservationFailedEvent`, `ProductRollbackDoneEvent` |
| `order-actions` | orchestrator | order-service | `CreateOrderAction`, `CancelOrderAction`, `UpdateOrderToPaidStatusAction` |
| `order-events` | order-service | orchestrator | `OrderCreatedEvent`, `OrderCreationFailedEvent`, `OrderCancellationDoneEvent`, `OrderStatusToPaidUpdatedEvent` |
| `payment-actions` | orchestrator | payment-service | `ProcessPaymentAction` |
| `payment-events` | payment-service | orchestrator | `PaymentProcessedEvent`, `PaymentProcessingFailedEvent` |

**Topic settings.** The orchestrator's `KafkaConfiguration` creates all six topics at startup with **3 partitions and 3 replicas**. This needs at least 3 brokers. Other services do not create topics.

**Message format.** Messages are JSON. The producer uses `StringSerializer` for keys and `JsonSerializer` for values. Consumers use `StringDeserializer` and `JsonDeserializer`, trusting the `com.saga.dto.*` package. Messages are sent without a key.

**Consumer groups.** Each service uses its own group ID: `saga-orchestrator-service`, `product-service`, `order-service`, `payment-service`.

---

## Messages (DTO module)

All messages live in `dto/src/main/java/com/saga/dto/`. They are Lombok `@Data` classes with no-args and all-args constructors.

**Actions (commands)**

| Class | Fields | Sent to |
|---|---|---|
| `ProductReserveAction` | `productId` | product-service |
| `ProductRollbackAction` | `productId` | product-service |
| `CreateOrderAction` | `productId`, `quantity` | order-service |
| `CancelOrderAction` | `orderId`, `productId` | order-service |
| `UpdateOrderToPaidStatusAction` | `orderId` | order-service |
| `ProcessPaymentAction` | `orderId`, `productId`, `amountToBePaid`, `creditCardNumber` | payment-service |

**Events (replies)**

| Class | Fields | Published by |
|---|---|---|
| `ProductReservedEvent` | `productId`, `quantity` | product-service |
| `ProductReservationFailedEvent` | `productId`, `details` | product-service |
| `ProductRollbackDoneEvent` | `productId` | product-service |
| `OrderCreatedEvent` | `orderId`, `productId`, `quantity`, `totalPrice` | order-service |
| `OrderCreationFailedEvent` | `productId`, `details` | order-service |
| `OrderCancellationDoneEvent` | `orderId`, `productId` | order-service |
| `OrderStatusToPaidUpdatedEvent` | `orderId` | order-service |
| `PaymentProcessedEvent` | `paymentId`, `orderId`, `productId` | payment-service |
| `PaymentProcessingFailedEvent` | `orderId`, `productId`, `details` | payment-service |

---

## Saga flow

### Happy path

```
Client        Orchestrator          Product            Order             Payment
  │ POST /buy     │                    │                  │                  │
  │──────────────►│ ProductReserveAction                  │                  │
  │               │───────────────────►│ stock -= 1       │                  │
  │               │ ProductReservedEvent                  │                  │
  │               │◄───────────────────│                  │                  │
  │               │ CreateOrderAction                     │                  │
  │               │──────────────────────────────────────►│ save order NEW   │
  │               │ OrderCreatedEvent                     │                  │
  │               │◄──────────────────────────────────────│                  │
  │               │ ProcessPaymentAction                  │                  │
  │               │─────────────────────────────────────────────────────────►│ save payment
  │               │ PaymentProcessedEvent                 │                  │
  │               │◄─────────────────────────────────────────────────────────│
  │               │ UpdateOrderToPaidStatusAction         │                  │
  │               │──────────────────────────────────────►│ status = PAID    │
  │               │ OrderStatusToPaidUpdatedEvent         │                  │
  │               │◄──────────────────────────────────────│                  │
  │               │ log: "FLOW FINISHED SUCCESSFULLY"     │                  │
```

### Failure and compensation

| Failure point | Event that triggers it | Orchestrator reaction | Compensations executed |
|---|---|---|---|
| Product reservation | `ProductReservationFailedEvent` | Logs the failure and stops | None. Nothing was changed yet |
| Order creation | `OrderCreationFailedEvent` | Sends `ProductRollbackAction` | Stock +1, then `ProductRollbackDoneEvent` (logged) |
| Payment | `PaymentProcessingFailedEvent` | Sends `CancelOrderAction` | Order → `CANCELLED`, then `OrderCancellationDoneEvent` triggers `ProductRollbackAction` (stock +1) |

Compensation runs in reverse order: each compensation is triggered by the failure or cancellation event of the step after it, and the orchestrator in `KafkaEventHandler` turns that event into the next compensating action.

### Where each step is handled

| Step | Orchestrator handler (`KafkaEventHandler`) | Service handler |
|---|---|---|
| Reserve stock | `handleProductEvent(ProductReservedEvent)` | `ProductHandler.handleProductAction(ProductReserveAction)` |
| Create order | `handleProductEvent` sends `CreateOrderAction`, then `handleOrderEvent(OrderCreatedEvent)` | `OrderHandler.handleOrderAction(CreateOrderAction)` |
| Pay | `handleOrderEvent(OrderCreatedEvent)` | `PaymentHandler.handlePaymentAction(ProcessPaymentAction)` |
| Mark paid | `handlePaymentEvent(PaymentProcessedEvent)` | `OrderHandler.handleOrderAction(UpdateOrderToPaidStatusAction)` |

---

## Order and product state

**Order statuses** (`order-service`, enum `OrderStatus`)

```
           createOrder             payOrder
  (none) ───────────────► NEW ───────────────► PAID
                           │
                           │ cancelOrder
                           └────────────────► CANCELLED
```

**Stock** (`product-service`, `ProductEntity.quantity`)

- `buyProduct`: `quantity - 1`
- `rollbackProduct`: `quantity + 1`
- There is no check for going below zero (see [Known limitations](#known-limitations)).

---

## Data model and seed data

Each service creates its schema with Hibernate (`ddl-auto` default for embedded databases). Product also runs `schema.sql` and `data.sql` at startup, because `spring.jpa.defer-datasource-initialization=true`.

| Service | Table | Columns |
|---|---|---|
| product-service | `product_entity` | `id` UUID (PK), `name` VARCHAR(255), `quantity` INT |
| order-service | `order_entity` | `order_id` UUID (PK), `product_id` UUID, `quantity` INT, `order_status` VARCHAR |
| payment-service | `payment_entity` | `payment_id` UUID (PK), `product_id` UUID, `order_id` UUID, `amount` DOUBLE |

**Seed products** (`product-service/src/main/resources/data.sql`)

| ID | Name | Initial quantity |
|---|---|---|
| `550e8400-e29b-41d4-a716-446655440000` | Product A | 100 |
| `550e8400-e29b-41d4-a716-446655440001` | Product B | 50 |
| `550e8400-e29b-41d4-a716-446655440002` | Product C | 200 |

Data lives in memory, so every restart resets the database and the stock.

---

## Configuration reference

Each module has `src/main/resources/application.properties`.

| Property | Modules | Example | Purpose |
|---|---|---|---|
| `server.port` | all services | `8080` / `8081` / `8082` / `8083` | HTTP port |
| `spring.application.name` | all | `order-service` | Application name (`PaymentService` in payment-service, a naming inconsistency) |
| `spring.kafka.bootstrap-servers` | all | `localhost:9092,localhost:9094,localhost:9096` | Kafka brokers |
| `spring.kafka.consumer.group-id` | all | `order-service` | Consumer group |
| `spring.kafka.consumer.*` | all | String/Json deserializers, trusted packages `com.saga.dto.*` | Deserialization |
| `spring.kafka.producer.*` | orchestrator | `acks=all`, idempotence enabled, retries via `delivery.timeout.ms=120000` | Producer reliability |
| `spring.datasource.url` | product, order, payment | `jdbc:h2:mem:order;DB_CLOSE_DELAY=-1` | In-memory database |
| `spring.datasource.username` / `password` | product, order, payment | `sa` / (empty) | H2 credentials |
| `spring.jpa.defer-datasource-initialization` | product, order, payment | `true` | Run `data.sql` after Hibernate creates tables |
| `spring.h2.console.enabled` / `path` | product, order, payment | `true` / `/h2-console` | H2 web console |
| `kafka.topic.name.<service>.actions` | all | `product-actions` | Action topic name |
| `kafka.topic.name.<service>.events` | all | `product-events` | Event topic name |

The orchestrator is the only service that needs all six topic names. Each business service needs only its own two.

---

## Running locally

### Prerequisites

- **JDK 27** (or 25 or later, see [Build notes](#build-notes))
- **Maven 3.6.3+** (minimum required by Spring Boot 3.4)
- **A Kafka cluster with 3 brokers** at `localhost:9092`, `localhost:9094` and `localhost:9096`. The repository does not include Kafka or a Docker setup. The orchestrator creates the topics on startup, but the cluster must have at least 3 brokers, because the topics use replication factor 3.

### Build

```bash
mvn clean install
```

### Start the services

Start the business services first, so they are listening when the orchestrator starts sending. Each command runs in its own terminal.

```bash
mvn -pl product-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl store-orchestrator-service spring-boot:run
```

Before the orchestrator starts, Kafka must be reachable, or the producer fails and the topics are not created.

---

## Running the tests

```bash
mvn test                                    # all modules
mvn -pl store-orchestrator-service -am test # one module, with its dependencies
```

Each service has unit tests in `src/test/java`. They mock `KafkaTemplate` and the repositories, so no Kafka cluster, database or running service is needed.

The tests cover the orchestrator's event-to-action mapping in `KafkaEventHandler`, its controller, and the handlers and services of the order, payment and product services.

---

## Trying it out

1. Open the H2 consoles (optional):

   | Service | URL | JDBC URL | User | Password |
   |---|---|---|---|---|
   | product | http://localhost:8081/h2-console | `jdbc:h2:mem:product` | `sa` | (empty) |
   | order | http://localhost:8082/h2-console | `jdbc:h2:mem:order` | `sa` | (empty) |
   | payment | http://localhost:8083/h2-console | `jdbc:h2:mem:payment` | `sa` | (empty) |

2. Buy a product:

   ```bash
   curl -X POST http://localhost:8080/store/buy \
     -H 'Content-Type: application/json' \
     -d '{"productId": "550e8400-e29b-41d4-a716-446655440000"}'
   ```

3. Watch the logs. A successful run logs, in order: `buySomething`, `handleProductEvent` (reserved), `handleOrderEvent` (created), `handlePaymentEvent` (processed), `handleOrderEvent` (paid), then `FLOW FINISHED SUCCESSFULLY`.

4. Check the result: `product_entity` quantity decreased by 1, a new `order_entity` row has status `PAID`, and a `payment_entity` row exists.

Payment cannot fail in the current code, so the compensation path only runs if you add a failure (see the commented-out line in `PaymentServiceImpl`) or stop a service mid-flow.

---

## Build notes

- `java.version` is 27, but the compiler targets `maven.compiler.release` **25**. Spring Boot 3.4.4's repackage plugin cannot read Java 27 class files (major version 71) and fails with `Unsupported class file major version 71`.
- Lombok is 1.18.48. Version 1.18.36 failed on JDK 26/27 with a `TypeTag :: UNKNOWN` error during compilation.
- Code is formatted with **google-java-format** (v2.0.0 at the time of writing). The `dto` module is a plain library and builds with the rest of the reactor.
- Unit tests use JUnit 6 and Mockito 5, pinned in the parent POM. `byte-buddy.version` is pinned to 1.18.17 for Java 27 support.
- There are no integration tests.

---

## Known limitations

This is a demo. The following are known gaps, several of them marked with `HERE WE SHOULD THROW A RETRYABLE EXCEPTION` in the code.

**Error handling and compensation**

- `OrderHandler` (order-service) swallows exceptions when cancelling or paying an order, and still sends the "done" event. Compensation can therefore report success while the order was not changed.
- `ProductHandler` (product-service) does the same for rollback. A failed rollback is reported as `ProductRollbackDoneEvent`.
- In `OrderHandler.handleOrderAction(CreateOrderAction)`, the order is saved before the reply is sent. If sending `OrderCreatedEvent` fails, `OrderCreationFailedEvent` is sent, and stock is rolled back, but the order stays `NEW` in the database.
- Payment failures are only reported by `PaymentHandler`. In the current code the payment service never fails, because `PaymentServiceImpl.pay` has the failure line commented out.

**Consistency and concurrency**

- `ProductServiceImpl.buyProduct` and `rollbackProduct` read, modify and save the entity without a transaction or locking. Concurrent purchases can lose updates.
- Stock can go negative. `buyProduct` does not check `quantity > 0`.
- If a product does not exist, `buyProduct` only logs, and `ProductReservedEvent` is still sent. The saga then creates an order for a product that was never reserved.
- Kafka consumers use at-least-once delivery, and handlers are not idempotent. A redelivered `ProductReserveAction` decrements stock twice.
- Saga state is not stored anywhere. If a message is lost or a service is down, the saga stops, with no timeout, retry or dead-letter topic.

**Orchestrator**

- `StoreController` sends the action and returns `200 OK` without checking the send result.
- The orchestrator has JPA and H2 dependencies but no entities, so they are unused.

**Simulated data and values**

- `OrderHandler` uses `new Random().nextDouble()` as the order's total price, so prices are between 0 and 1.
- `CreditCardUtils` generates a random 16-digit card number. `PaymentServiceImpl` ignores it, and it is passed through Kafka messages as plain text.
- The credit card number is logged as part of `ProcessPaymentAction`'s `toString()` in the orchestrator and payment logs, because the DTOs are `@Data`.

**Other**

- `spring.application.name` is `PaymentService` in payment-service, while the other services use lowercase hyphenated names.
- `util.CreditCardUtils` lives in the package `util`, outside the `com.saga` package hierarchy.
- No integration tests, no CI and no containerisation are included.

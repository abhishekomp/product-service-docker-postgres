# Product Service — Spring Boot + Docker + PostgreSQL

A learning project that demonstrates how to build a **REST API with Spring Boot**, persist data in **PostgreSQL**, package everything with **Docker**, and write **integration tests using Testcontainers** — all in one place.

---

📖 **New to some of these concepts? Start here:** [Concepts.md](./Concepts.md) explains every term — Spring Boot, JPA, Docker, REST, MockMvc, AssertJ — with how they all connect in this project.

---

## 🎯 What you will learn from this project

| Concept | Where to look |
|---------|---------------|
| Layered architecture (Controller → Service → Repository) | `controller/`, `service/`, `repository/` |
| Full CRUD REST API with correct HTTP status codes | `ProductController.java` |
| Input validation (`@Valid`, `@NotBlank`) | `Product.java`, `ProductController.java` |
| Global error handling (`@RestControllerAdvice`) | `exception/GlobalExceptionHandler.java` |
| Structured error responses (Java Record) | `exception/ErrorResponse.java` |
| Spring Data JPA with PostgreSQL | `ProductRepository.java`, `application.properties` |
| Multi-stage Docker builds | `Dockerfile` |
| Running multiple containers together with Docker Compose | `docker-compose.yaml` |
| Integration testing with Testcontainers (4 different approaches) | `src/test/` |
| Seeding a database with `data.sql` | `src/main/resources/data.sql` |

---

## 🏗️ Project Architecture

```
HTTP Request
     │
     ▼
ProductController        ← receives HTTP requests, returns HTTP responses
     │
     ▼
ProductService           ← business logic lives here
     │
     ▼
ProductRepository        ← Spring Data JPA interface, talks to the DB
     │
     ▼
PostgreSQL Database      ← stores product data
```

**Package layout:**
```
org.aom.product
├── controller/   ProductController.java   — REST endpoints
├── service/      ProductService.java      — business logic
├── repository/   ProductRepository.java   — data access
└── model/        Product.java             — the JPA entity (maps to a DB table)
```

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language |
| Spring Boot | 3.3.2 | Application framework |
| Spring Data JPA | (via Boot) | ORM / database access |
| Hibernate | (via Boot) | JPA implementation |
| PostgreSQL | 16 | Relational database |
| Docker & Docker Compose | any recent | Containerisation |
| Testcontainers | 1.20.1 | Spin up real Postgres in tests |
| JUnit 5 | (via Boot) | Test framework |

---

## 🗄️ The Data Model

There is a single entity: **Product**.

```
┌──────────────────────────────────────────┐
│                  product                 │
├─────────────┬──────────────┬─────────────┤
│  prod_num   │   p_name     │  sku_code   │
│  (PK, seq)  │  (varchar)   │  (varchar)  │
├─────────────┼──────────────┼─────────────┤
│      1      │ Apple iPhone │  APP2024    │
│      2      │ Samsung S24  │  SAM2024    │
└─────────────┴──────────────┴─────────────┘
```

The table name and columns are derived automatically from the `Product` entity class by Hibernate.  
The two rows above are seeded on startup via `src/main/resources/data.sql`.

---

## 🚀 How to Run the Project

### Option A — Docker Compose (recommended, no local Postgres needed ✅)

> **You do NOT need PostgreSQL installed on your Mac.**  
> Docker Compose starts both the Spring Boot app and a PostgreSQL database in separate containers automatically.

**Prerequisites:** A Docker-compatible runtime installed and running — [Docker Desktop](https://www.docker.com/products/docker-desktop/), [Colima](https://github.com/abiosoft/colima) (`brew install colima && colima start`), [Rancher Desktop](https://rancherdesktop.io/), or [Podman Desktop](https://podman-desktop.io/).

**Steps:**

```bash
# 1. Clone / open the project
cd product-service-docker-postgres

# 2. Create your local .env file from the example (only needed once)
cp .env.example .env
# Then open .env and set your preferred password for POSTGRES_DB_PWD

# 3. Start everything (builds the app image + starts Postgres + starts the app)
docker compose up

# To run in the background (detached mode):
docker compose up -d

# To stop everything:
docker compose down
```

> **What happens behind the scenes:**
> - Docker builds the Spring Boot app using the multi-stage `Dockerfile` (no need to run Maven yourself).
> - A `postgres:16` container starts and creates the `productDb` database.
> - The Spring Boot container starts, connects to Postgres, creates the `product` table, and seeds the two initial rows.
> - The app is available at `http://localhost:8081`.

---

### Option B — Run locally (requires local PostgreSQL)

> **You DO need PostgreSQL running on your Mac for this option.**

If you have Postgres installed (e.g. via [Homebrew](https://brew.sh/): `brew install postgresql@16`):

```bash
# 1. Start your local Postgres (if not already running)
brew services start postgresql@16

# 2. Make sure a 'postgres' user and database exist (Homebrew sets this up by default)

# 3. Run the app
./mvnw spring-boot:run
```

The `application.properties` defaults to `jdbc:postgresql://localhost:5432/postgres` with username `postgres` / password `admin`.  
Adjust these if your local setup is different.

---

## 📡 API Endpoints

Base URL: `http://localhost:8081/product-service`

### Create a product — `POST /addProduct`
```
POST /addProduct
Content-Type: application/json

{
  "pName": "Google Pixel 9",
  "skuCode": "GOO2024"
}
```
Returns **201 Created** with the saved product (including the generated `prodNum`).  
Returns **400 Bad Request** if `pName` or `skuCode` is blank.

---

### Get a single product — `GET /getProduct/{id}`
```
GET /getProduct/1
```
Returns **200 OK** with the product.  
Returns **404 Not Found** with a structured error body if the ID does not exist.

---

### Get all products — `GET /getAllProducts`
```
GET /getAllProducts
```
Returns **200 OK** with a JSON array (empty array if no products exist).

---

### Update a product — `PUT /updateProduct/{id}`
```
PUT /updateProduct/1
Content-Type: application/json

{
  "pName": "Apple iPhone 17",
  "skuCode": "APP2025"
}
```
Returns **200 OK** with the updated product.  
Returns **404 Not Found** if the ID does not exist.  
Returns **400 Bad Request** if `pName` or `skuCode` is blank.

---

### Delete a product — `DELETE /deleteProduct/{id}`
```
DELETE /deleteProduct/1
```
Returns **204 No Content** on success (no body).  
Returns **404 Not Found** if the ID does not exist.

---

### Error response shape

All errors return a consistent JSON body:
```json
{
  "timestamp": "2024-08-16T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 99",
  "path": "/product-service/getProduct/99"
}
```

A Postman collection is included in the project root:  
`Product-Service-SpringBoot-docker.postman_collection.json` — import it into Postman to try all endpoints immediately.

---

## 🧪 Testing — Testcontainers

This project deliberately shows **four different ways** to use Testcontainers so you can compare them side by side.

> **Important:** Testcontainers starts a **real PostgreSQL Docker container** during tests.  
> You do not need a local Postgres running — but you **do need a Docker-compatible runtime running** (Docker Desktop, Colima, Rancher Desktop, etc.) when you run the tests.

| Test class | Approach |
|------------|----------|
| `ProductRepositoryTestContainerManualStartTest` | Manual lifecycle (`@BeforeAll` / `@AfterAll`) |
| `ProductRepositoryTestContainerTest` | Automatic lifecycle (`@Testcontainers` + `@Container`) |
| `ProductRepositoryWithTCJdbcUrlTest` | TC JDBC URL (`jdbc:tc:postgresql:16:///`) |
| `ProductControllerIntegrationUsesInitializerTest` | Shared custom initializer (`@ContextConfiguration`) |

📖 **For a full explanation of each approach, see [Testcontainers.md](./Testcontainers.md).**

### Run the tests
```bash
# Make sure your Docker runtime is running (Docker Desktop, Colima, etc.), then:
./mvnw test
```

---

## 🐳 Understanding the Dockerfile

```dockerfile
# ── Stage 1: Build ──────────────────────────────────────────────────
FROM maven:3.9.8-eclipse-temurin-21 AS buildstage
# Uses an official Maven + JDK 21 image to compile the project and
# produce a fat JAR (all dependencies bundled inside one .jar file).
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests   # builds the JAR, skips tests

# ── Stage 2: Runtime image ───────────────────────────────────────────
FROM eclipse-temurin:21-jre
# Uses a lean JRE-only image — no Maven, no source code, just the JRE.
# This keeps the final image small and reduces the attack surface.
WORKDIR /app
COPY --from=buildstage /app/target/*.jar ./app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why two stages?**  
The build stage needs Maven and the full JDK. The runtime stage only needs a JRE to run the already-compiled `.jar`. Keeping them separate means the final Docker image is much smaller.

---

## 🔧 Useful Docker Commands

```bash
# See running containers
docker ps

# See logs of a specific container
docker logs product_service_con
docker logs db

# Open a terminal inside a running container
docker exec -it db /bin/bash

# Connect to Postgres once inside the container
psql -U postgres -d productDb

# Useful psql commands once connected:
\l          # list all databases
\c productDb  # connect to productDb
\dt         # list all tables
SELECT * FROM product;  # query the product table
\q          # quit psql

# Stop and remove containers (keeps volumes)
docker compose down

# Stop and remove containers AND volumes (wipes the database)
docker compose down -v

# Rebuild the app image from scratch (e.g. after code changes)
docker compose up --build
```

---

## 📁 Project Structure

```
product-service-docker-postgres/
├── src/
│   ├── main/
│   │   ├── java/org/aom/product/
│   │   │   ├── ProductServiceApplication.java       ← entry point (@SpringBootApplication)
│   │   │   ├── controller/ProductController.java    ← REST endpoints
│   │   │   ├── service/ProductService.java          ← business logic
│   │   │   ├── repository/ProductRepository.java    ← Spring Data JPA interface
│   │   │   └── model/Product.java                  ← JPA entity
│   │   └── resources/
│   │       ├── application.properties               ← app config (port, datasource, JPA)
│   │       └── data.sql                             ← seed data inserted on startup
│   └── test/
│       └── java/org/aom/product/
│           ├── controller/                          ← full integration tests (MockMvc)
│           ├── repository/                          ← JPA layer tests (4 TC approaches)
│           └── infra/                               ← shared Testcontainers initializer
├── Dockerfile                                       ← multi-stage build
├── docker-compose.yaml                              ← runs app + postgres together
├── pom.xml                                          ← Maven dependencies
└── Product-Service-SpringBoot-docker.postman_collection.json
```

---

## ❓ FAQ

**Q: Do I need PostgreSQL installed on my Mac?**  
A: **No**, if you use `docker compose up`. Docker manages both the app and the database.  
**Yes**, only if you want to run the app directly with `./mvnw spring-boot:run` (Option B above).

**Q: Do I need Docker running to execute the tests?**  
A: **Yes.** The tests use Testcontainers, which pulls and starts a real `postgres:16` Docker container automatically. Make sure your Docker-compatible runtime (Docker Desktop, Colima, Rancher Desktop, etc.) is running before running `./mvnw test`.

**Q: The app starts but I get a connection error.**  
A: When using Docker Compose, the app sometimes starts before Postgres is fully ready. The `restart: always` policy in `docker-compose.yaml` handles this — the app container will restart until it can connect. Wait a few seconds and it will come up.

**Q: I changed the code. How do I rebuild the Docker image?**  
A: Run `docker compose up --build`. This forces Docker to rebuild the app image from the updated source code.

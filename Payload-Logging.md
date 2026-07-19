# Logging HTTP Payloads in Spring Boot

> How to capture every incoming request and outgoing response, where to store the logs,
> and how to read them — explained from first principles for someone brand new to logging.

---

## Table of Contents

1. [What Is a Log? — Starting from Zero](#1-what-is-a-log--starting-from-zero)
2. [Why Payload Logging Matters](#2-why-payload-logging-matters)
3. [The Core Challenge — Streams Can Only Be Read Once](#3-the-core-challenge--streams-can-only-be-read-once)
4. [The Solution — ContentCachingWrapper](#4-the-solution--contentcachingwrapper)
5. [Servlet Filters — Intercepting Every Request](#5-servlet-filters--intercepting-every-request)
6. [OncePerRequestFilter — The Right Base Class](#6-onceperequestrequest-filter--the-right-base-class)
7. [Threads and MDC — Tying Log Lines to One Request](#7-threads-and-mdc--tying-log-lines-to-one-request)
8. [How PayloadLoggingFilter Works — Line by Line](#8-how-payloadloggingfilter-works--line-by-line)
9. [How Spring Picks Up the Filter Automatically](#9-how-spring-picks-up-the-filter-automatically)
10. [Logback — Controlling Where Logs Go](#10-logback--controlling-where-logs-go)
11. [Where Logs Are Stored](#11-where-logs-are-stored)
12. [Try It — See the Logs in Action](#12-try-it--see-the-logs-in-action)
13. [Reading Logs in Docker](#13-reading-logs-in-docker)
14. [Log Levels — How to Control Verbosity](#14-log-levels--how-to-control-verbosity)
15. [What You Should NOT Log](#15-what-you-should-not-log)
16. [Sample Log Output](#16-sample-log-output)
17. [Quick Reference](#17-quick-reference)

---

## 1. What Is a Log? — Starting from Zero

A **log** is a continuous stream of timestamped text messages that a running application writes to record what it is doing.

Think of it like a diary that the application keeps automatically:

```
2024-08-16 14:32:01  Application started on port 8081
2024-08-16 14:32:05  Received GET /getAllProducts
2024-08-16 14:32:05  Hibernate executed: SELECT * FROM product
2024-08-16 14:32:05  Returned 2 products, took 44ms
2024-08-16 14:35:12  Received POST /addProduct with body {"pName":"Google Pixel 9"}
2024-08-16 14:35:12  Product saved with id 3
```

Each line is a **log entry** (also called a log event or log record).

### Where do logs go?

By default, Spring Boot prints log lines to the **console** (your terminal). This is fine while you are actively watching, but:
- When you close the terminal the logs are gone
- You cannot search or scroll back through a long history
- In a Docker container you cannot even see the console

That is why we also configure logs to be written to a **log file** on disk. The file persists even when the app restarts, can be searched with tools like `grep`, and can be read from your Mac even when the app is running inside Docker.

### What is a log file?

A log file is just a plain text file where each line is one log entry. In this project, the log file lives at:
```
product-service-docker-postgres/
└── logs/
    └── app.log       ← the current active log file
    └── app-2024-08-15.0.log.gz  ← yesterday's log, compressed
```

You can open `app.log` in any text editor, or stream it live in your terminal.

### What is "payload logging" specifically?

**Payload** = the data. Specifically:
- **Request payload** = the JSON body the client sends (e.g. `{"pName": "Apple iPhone"}`)
- **Response payload** = the JSON body the server sends back

Payload logging = capturing and recording those JSON bodies so you have a record of every request and every response, not just that a request happened.

---

## 2. Why Payload Logging Matters

When something goes wrong in a REST API, the first questions are always:

- What request did the client actually send?
- What did the server actually return?
- How long did it take?

Without payload logging, the only answers come from asking the client to reproduce the issue, or guessing from error messages. With it, you can open the log file and immediately see the exact JSON that arrived and the exact JSON that went back.

**Payload logging is useful for:**
- Debugging unexpected behaviour ("why did this product get saved with a blank name?")
- Auditing ("who created or deleted this record and when?")
- Performance investigation ("which endpoints are slow?")
- Support tickets ("can you reproduce?" → "I don't need to, I have the log")

---

## 3. The Core Challenge — Streams Can Only Be Read Once

Here is the fundamental problem when you try to read HTTP request and response bodies.

### What is an InputStream?

An HTTP request body in Java is delivered as an **`InputStream`** — imagine a water pipe. Water flows through it in one direction. Once the water has flowed through, there is no way to make it flow backwards. You cannot "rewind" and read it again.

```
Request arrives
     │
     ▼
InputStream (the pipe)
     │
     ├─── YOU read it for logging → pipe is now empty
     │
     └─── Controller tries to read it → gets nothing ❌
```

The same problem exists for the response body — it is an **`OutputStream`**, a pipe going the other way:

```
Controller writes response body into OutputStream
     │
     └── flows out to the client immediately
          │
          └── You try to read it for logging → nothing left ❌
```

This is why the obvious approach does not work:

```java
// BROKEN — this empties the input stream before the controller sees it
String body = new String(request.getInputStream().readAllBytes());
logger.info("Body: {}", body);
// The controller now reads an empty body 💥
```

---

## 4. The Solution — `ContentCachingWrapper`

Spring provides two wrapper classes specifically to solve the "stream can only be read once" problem. Think of them as **recording devices** that sit on the pipe.

### `ContentCachingRequestWrapper`

Instead of handing the original request to the controller, we hand it a wrapped version.
The wrapper intercepts every byte flowing through the pipe and silently copies them into an internal buffer — like a splitter that records the water while still letting it flow normally.

```
Request arrives
     │
     ▼
ContentCachingRequestWrapper (the recording splitter)
     │
     ├── forwards bytes to controller as normal ✅
     └── also copies bytes into internal buffer
                    │
                    └── getContentAsByteArray()  ← read for logging AFTER controller runs ✅
```

### `ContentCachingResponseWrapper`

Does the same for the response. The controller writes the response body into the wrapper.
The wrapper buffers the bytes instead of sending them immediately — holding them until we are ready.

```
Controller writes response body
     │
     ▼
ContentCachingResponseWrapper (holds the bytes)
     │
     ├── bytes available for logging via getContentAsByteArray() ✅
     └── copyBodyToResponse() → releases the held bytes to the client ✅
```

> ⚠️ **Critical:** You MUST call `responseWrapper.copyBodyToResponse()` when you are done.
> Without this call, the client receives an empty response. The bytes were captured but never
> actually sent. This is the most common mistake when implementing this pattern.

---

## 5. Servlet Filters — Intercepting Every Request

### What is a Servlet?

A **Servlet** is a Java class that handles HTTP requests. Spring Boot's controllers are built on top of servlets. Your `ProductController` is ultimately served by a servlet engine called **Tomcat**, which Spring Boot bundles inside the application (you do not install Tomcat separately — it is inside the `.jar` file).

### What is a Filter?

A **Servlet Filter** is a component that sits in front of all controllers. Every HTTP request passes through the filter chain before reaching any controller. Every response passes back through the filters on the way out.

```
HTTP Request from client
         │
         ▼
┌────────────────────────────────────────────┐
│              Filter Chain                  │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │  PayloadLoggingFilter  (ours)        │  │  ← intercepts every request/response
│  └─────────────────┬────────────────────┘  │
│                    │                       │
│  ┌─────────────────▼────────────────────┐  │
│  │  Spring Security Filter (if added)   │  │
│  └─────────────────┬────────────────────┘  │
│                    │                       │
└────────────────────│───────────────────────┘
                     │
                     ▼
             DispatcherServlet
                     │
                     ▼
             ProductController
                     │
                     ▼
             ProductService → ProductRepository → Database
```

The key insight: **a single filter automatically applies to every endpoint**. When you add a new endpoint tomorrow, it is automatically logged. No changes needed.

Compare this with logging inside each controller method:
- Logging in the filter: one class, zero duplication ✅
- Logging in each controller: copied code in every method, easy to forget ❌

This is what developers mean by a **"cross-cutting concern"** — something that needs to apply across the whole application (authentication, logging, error handling), not just in one place.

---

## 6. `OncePerRequestFilter` — The Right Base Class

Spring provides a convenient base class called `OncePerRequestFilter`. When you extend it, you only need to implement one method: `doFilterInternal()`.

```java
@Component
public class PayloadLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        // your logging logic goes here
    }
}
```

The "Once" in `OncePerRequestFilter` is a guarantee: even in complex web scenarios where Spring internally forwards a request from one servlet to another (which could trigger a naive filter twice), this base class ensures `doFilterInternal` is only called **once per original HTTP request** from the client.

---

## 7. Threads and MDC — Tying Log Lines to One Request

### What is a Thread?

When your Spring Boot application starts, Tomcat creates a pool of **threads** — think of them as workers. When a request arrives, one worker picks it up and handles it from start to finish. When done, the worker goes back to the pool to wait for the next request.

If 10 requests arrive at the same time, 10 workers handle them simultaneously. Their log lines are interleaved in the log file in whatever order they happen to execute:

```
INFO  [worker-1] ProductController - getAllProducts invoked
INFO  [worker-3] ProductController - getAllProducts invoked
INFO  [worker-2] ProductService    - saving product: Apple iPhone
INFO  [worker-1] ProductRepository - SELECT * FROM product
INFO  [worker-3] ProductRepository - SELECT * FROM product
```

Looking at this, can you tell which "getAllProducts" produced which database query? **No — it is impossible.** This is the problem MDC solves.

### What is MDC?

**MDC** stands for **Mapped Diagnostic Context**. It is a small dictionary (key → value map) that belongs to one worker thread. You store a value in it at the start of a request, Logback automatically includes it in every log line produced by that worker, and you clear it at the end.

```java
// At the start of the request (in the filter):
MDC.put("requestId", "a3f7c2d1");   // store a unique ID for this request

// Every single log line produced on this thread now automatically includes [a3f7c2d1]:
// INFO  [a3f7c2d1] ProductController  - getAllProducts invoked
// INFO  [a3f7c2d1] ProductRepository  - SELECT * FROM product
// INFO  [a3f7c2d1] PayloadLoggingFilter - << OUTGOING RESPONSE Status: 200

// At the end of the request:
MDC.clear();  // ← IMPORTANT: must clear it so the next request on this worker
              //   does not inherit the previous request's ID
```

Now the logs for concurrent requests are clearly separated:
```
INFO  [a3f7c2d1] ProductController - getAllProducts invoked
INFO  [b9e1f4a2] ProductController - getAllProducts invoked
INFO  [a3f7c2d1] ProductRepository - SELECT * FROM product
INFO  [b9e1f4a2] ProductRepository - SELECT * FROM product
```

To find ALL log lines for one specific request, you simply:
```bash
grep "a3f7c2d1" logs/app.log
```

This returns every log line tagged with that ID — from the filter, through the controller, the service, and the repository — all in one place.

The pattern `%X{requestId:---------}` in `logback-spring.xml` tells Logback:
- `%X{requestId}` → include the MDC value stored under the key "requestId"
- `:---------` → if no requestId is in MDC (e.g. app startup logs), use `---------` as a placeholder

---

## 8. How `PayloadLoggingFilter` Works — Line by Line

Here is the complete flow of `doFilterInternal`, broken down step by step:

```java
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {

    // ── Step 1: Wrap the request and response ────────────────────────────
    // Replace the original request and response with our recording wrappers.
    // From this point on, all reads/writes go through the wrappers,
    // which buffer the bytes while still letting them flow normally.
    ContentCachingRequestWrapper requestWrapper  = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    // ── Step 2: Assign a unique requestId and store it in MDC ────────────
    // UUID.randomUUID() generates a globally unique ID like "a3f7c2d1-b9e1-..."
    // We take only the first 8 characters to keep log lines readable.
    // MDC.put() makes this ID available to ALL log lines on this thread.
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put("requestId", requestId);

    // Record the current time in milliseconds so we can calculate duration later.
    long startTime = System.currentTimeMillis();

    try {
        // ── Step 3: Let the request continue to the controller ───────────
        // filterChain.doFilter() passes the (wrapped) request and response
        // to the next filter in the chain, and eventually to the controller.
        //
        // This is where the controller method runs, reads the request body,
        // calls the service, queries the database, and writes the response.
        //
        // By the time this line returns, the entire request has been processed.
        filterChain.doFilter(requestWrapper, responseWrapper);

    } finally {
        // The finally block runs whether the above succeeded or threw an exception.
        // This guarantees we always log — including for failed requests.
        long durationMs = System.currentTimeMillis() - startTime;

        // ── Step 4: Log the request ──────────────────────────────────────
        // NOW we can read the request body from the wrapper's internal buffer.
        // Before step 3, this buffer would have been empty because the
        // controller had not yet read the stream.
        logRequest(requestWrapper);

        // ── Step 5: Log the response ─────────────────────────────────────
        // The response body is in the wrapper's buffer — the controller
        // wrote to the wrapper, not directly to the client.
        logResponse(responseWrapper, durationMs);

        // ── Step 6: Send the response to the client ──────────────────────
        // ⚠ CRITICAL — without this line the client gets an empty response.
        // The wrapper held the bytes; this forwards them to the real client.
        responseWrapper.copyBodyToResponse();

        // ── Step 7: Clean up MDC ─────────────────────────────────────────
        // Clear the requestId from this thread's MDC.
        // Without this, the NEXT request handled by the same worker thread
        // would inherit the previous request's ID — confusing the logs.
        MDC.clear();
    }
}
```

**Two important "why?" questions:**

> **Why log AFTER `filterChain.doFilter()`?**
> `ContentCachingRequestWrapper` fills its buffer as the controller reads the stream.
> Before `filterChain.doFilter()` runs, the controller has not read anything yet,
> so the buffer is empty. We must wait until AFTER the controller runs to read the buffer.

> **Why use `finally`?**
> If the controller throws an unexpected exception, the code after `filterChain.doFilter()`
> would normally be skipped. `finally` guarantees our logging and cleanup code runs
> regardless of whether an exception was thrown. This is most important because
> failed requests are exactly the ones you most want to see in the logs.

---

## 9. How Spring Picks Up the Filter Automatically

You might wonder: we wrote a filter class — but how does Spring know to use it? We never registered it anywhere explicitly.

The answer is the `@Component` annotation on the class:

```java
@Component   // ← this one annotation does the work
public class PayloadLoggingFilter extends OncePerRequestFilter {
```

When Spring Boot starts, it performs **component scanning** — it scans all packages under your main application class for classes annotated with `@Component`, `@Service`, `@Repository`, `@RestController`, etc. When it finds a class that is a `@Component` AND extends `Filter` (which `OncePerRequestFilter` does), it automatically registers it in the servlet filter chain.

You do not need to write any configuration class or XML. One annotation, and Spring handles everything:
1. Creates an instance of `PayloadLoggingFilter`
2. Registers it as a filter in the embedded Tomcat
3. Ensures it is called for every HTTP request

---

## 10. Logback — Controlling Where Logs Go

### What is Logback?

**Logback** is the logging library that Spring Boot includes by default. It is the engine that takes every `logger.info(...)` call from your code and decides:
- Should this line be shown or suppressed? (based on log level)
- Where should it be written? (console, file, or both)
- What should it look like? (the format/pattern)

You configure Logback in `src/main/resources/logback-spring.xml`.
Spring Boot loads this file automatically on startup.

### Key concepts

**Appender** — think of it as a destination. You can have multiple appenders active at once:

| Appender | What it is | Where it writes |
|----------|-----------|----------------|
| `CONSOLE` | Writes to standard output | Your terminal or `docker logs` command |
| `FILE` | Writes to a file on disk | `logs/app.log` (with automatic rolling) |

Both appenders are active in this project, so every log line goes to both the terminal AND the file simultaneously.

**Logger** — each logger is associated with a Java package or class. It controls the minimum level for that code:

```xml
<!-- Only show INFO and above from our application code -->
<logger name="org.aom.product" level="INFO"/>

<!-- Spring's internal code is very chatty — only show warnings -->
<logger name="org.springframework" level="WARN"/>
```

**Root logger** — the fallback. If a class is not covered by any specific logger above, this rule applies:

```xml
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
```

**Rolling policy** — the FILE appender automatically manages disk space so logs do not grow forever:

```xml
<maxFileSize>10MB</maxFileSize>    <!-- Start a new file when current one hits 10 MB -->
<maxHistory>30</maxHistory>         <!-- Delete files older than 30 days automatically -->
<totalSizeCap>500MB</totalSizeCap>  <!-- If all log files together exceed 500 MB, delete oldest -->
```

When a file rolls, it is renamed with the date and compressed:
`app.log` → `app-2024-08-15.0.log.gz`

### The log pattern decoded

```
%d{yyyy-MM-dd HH:mm:ss.SSS}

  %d   = date/time
  yyyy = 4-digit year
  MM   = 2-digit month
  dd   = 2-digit day
  HH   = hour (24h)
  mm   = minute
  ss   = second
  SSS  = milliseconds
  Example: 2024-08-16 14:32:01.123

[%X{requestId:---------}]

  %X{requestId}  = value from MDC under key "requestId"
  :---------     = fallback if requestId is not set
  Example: [a3f7c2d1]

[%thread]

  The name of the thread handling this request.
  Example: [http-nio-8081-exec-3]

%-5level

  The log level, padded to 5 characters for alignment.
  Example: INFO   WARN   ERROR

%logger{36}

  The class name that produced this log line.
  Truncated to 36 characters.
  Example: o.a.p.filter.PayloadLoggingFilter

%msg

  The actual message passed to logger.info(...) / logger.warn(...) etc.

%n

  A newline character — ends the line.
```

A real complete log line:
```
2024-08-16 14:32:01.123 [a3f7c2d1] [http-nio-8081-exec-3] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | POST /product-service/addProduct | Content-Type: application/json | Body: {"pName":"Google Pixel 9","skuCode":"GOO2024"}
2024-08-16 14:32:01.456 [a3f7c2d1] [http-nio-8081-exec-3] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 201 | Duration: 333ms | Body: {"prodNum":3,"skuCode":"GOO2024","pName":"Google Pixel 9"}
```

---

## 11. Where Logs Are Stored

### When running locally (`./mvnw spring-boot:run`)

Logs are written to the `logs/` folder in your project root:
```
product-service-docker-postgres/
└── logs/
    └── app.log         ← grows as the app runs
```

The `logs/` folder is **git-ignored** (in `.gitignore`) so log files are never accidentally committed to the repository. However, a file called `logs/.gitkeep` is committed — its only purpose is to ensure the `logs/` folder itself exists when someone clones the project. Git does not track empty folders; `.gitkeep` is the conventional trick to work around this.

### When running with Docker Compose

The `docker-compose.yaml` uses a **volume mount**:

```yaml
volumes:
  - ./logs:/app/logs
```

A volume mount connects a folder on **your Mac** (`./logs`) to a folder **inside the Docker container** (`/app/logs`). They point to the same files. Writing a log file inside the container instantly appears on your Mac, and vice versa.

```
Your Mac (host):              Docker container:
./logs/app.log   ←──────→   /app/logs/app.log
./logs/app-2024-08-15.0.log.gz   /app/logs/app-2024-08-15.0.log.gz
```

**Why this matters:** Without the volume mount, log files would be trapped inside the container. When you run `docker compose down`, the container is destroyed and all its files are deleted — including the logs. With the volume mount, log files live on your Mac and survive forever.

### Log file lifecycle

```
Day 1 (today):
  app.log  ← all new log lines go here, file grows throughout the day

Day 2 arrives (midnight rollover):
  app-2024-08-16.0.log.gz  ← yesterday's log, automatically compressed
  app.log                  ← new empty file for today

If file hits 10 MB before midnight:
  app-2024-08-16.0.log.gz  ← first file for today
  app-2024-08-16.1.log.gz  ← second file (if it also hits 10 MB)
  app.log                  ← current active file

Day 31:
  app-2024-07-17.0.log.gz  ← deleted automatically (older than 30 days)
```

---

## 12. Try It — See the Logs in Action

Once the app is running, follow these steps to see payload logging working.

### Step 1 — Start the app

```bash
# Option A: locally (needs local Postgres)
./mvnw spring-boot:run

# Option B: with Docker Compose
cp .env.example .env   # first time only
docker compose up
```

### Step 2 — Open a second terminal and watch the log file

```bash
# "tail" reads the end of a file.
# The "-f" flag means "follow" — keep watching for new lines in real time.
# New log entries appear as soon as they are written.
tail -f logs/app.log
```

Leave this terminal open. Every HTTP request you make will appear here within milliseconds.

### Step 3 — Send requests using Postman or curl

**Add a product (POST):**
```bash
curl -X POST http://localhost:8081/product-service/addProduct \
     -H "Content-Type: application/json" \
     -d '{"pName":"Google Pixel 9","skuCode":"GOO2024"}'
```

**Get all products (GET):**
```bash
curl http://localhost:8081/product-service/getAllProducts
```

**Try a product that does not exist (should produce a 404):**
```bash
curl http://localhost:8081/product-service/getProduct/999
```

**Send invalid data (should produce a 400):**
```bash
curl -X POST http://localhost:8081/product-service/addProduct \
     -H "Content-Type: application/json" \
     -d '{"pName":"","skuCode":"GOO2024"}'
```

### Step 4 — Look at the log output

In your watching terminal you will see something like:

```
2024-08-16 14:32:01.123 [a3f7c2d1] [...] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | POST /product-service/addProduct | Content-Type: application/json | Body: {"pName":"Google Pixel 9","skuCode":"GOO2024"}
2024-08-16 14:32:01.456 [a3f7c2d1] [...] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 201 | Duration: 333ms | Body: {"prodNum":3,"skuCode":"GOO2024","pName":"Google Pixel 9"}
```

Notice: both lines share `[a3f7c2d1]` — the same requestId. That is the MDC in action.

### Step 5 — Search the log file

```bash
# Find all log lines for one specific request
grep "a3f7c2d1" logs/app.log

# Find all 404 responses
grep "Status: 404" logs/app.log

# Find all POST requests
grep "INCOMING REQUEST.*POST" logs/app.log
```

---

## 13. Reading Logs in Docker

### Stream live logs from the running container

The `docker logs` command shows the CONSOLE appender output (stdout):

```bash
# Show all past logs from the app container
docker logs product_service_con

# Stream new log lines in real time (press Ctrl+C to stop)
docker logs -f product_service_con

# Show only the last 100 lines
docker logs --tail 100 product_service_con
```

### Read the log file directly from your Mac

Because `./logs` is volume-mounted, the `app.log` file exists on your Mac:

```bash
# Follow the log file in real time
tail -f logs/app.log

# See only incoming requests
grep "INCOMING REQUEST" logs/app.log

# See only outgoing responses
grep "OUTGOING RESPONSE" logs/app.log

# Find all log lines for one specific requestId
grep "a3f7c2d1" logs/app.log

# Find all 404 responses
grep "Status: 404" logs/app.log

# List all log files (current + rolled archives)
ls -lh logs/
```

### Open a terminal inside the running container

```bash
# Enter the container's shell
docker exec -it product_service_con /bin/bash

# Inside the container — same commands work
tail -f /app/logs/app.log
ls /app/logs/
```

---

## 14. Log Levels — How to Control Verbosity

There are five log levels, ordered from least to most severe:

```
TRACE  →  DEBUG  →  INFO  →  WARN  →  ERROR
(most verbose)                    (least verbose)
```

When you set a logger to a level, only messages at that level or higher are shown:

| Level set to | You see | You do not see |
|-------------|---------|---------------|
| `TRACE` | Everything | Nothing hidden |
| `DEBUG` | DEBUG, INFO, WARN, ERROR | TRACE |
| `INFO` | INFO, WARN, ERROR | TRACE, DEBUG |
| `WARN` | WARN, ERROR | TRACE, DEBUG, INFO |
| `ERROR` | ERROR only | Everything else |

**`INFO` is the default** and is right for most situations. You see important events without being overwhelmed by the internal details.

### When to use each level in your own code

```java
logger.trace("Entering method buildQuery with params: {}", params);    // ultra-detailed, dev only
logger.debug("Found {} products matching filter", count);               // useful during development
logger.info("Request received: {} {}", method, uri);                    // normal operations
logger.warn("Product not found with id {}, returning 404", id);        // something unexpected but handled
logger.error("Failed to connect to database after 3 retries", ex);     // something broken
```

### In this project

| Logger | Level | Why |
|--------|-------|-----|
| `org.aom.product` | `INFO` | Our app code — see all `logger.info()` calls |
| `org.hibernate.SQL` | `DEBUG` | Shows the actual SQL Hibernate runs |
| `org.springframework` | `WARN` | Spring internals are very chatty — only show problems |
| `org.aom.product.filter` (prod profile) | `WARN` | Silences payload logging in production (security + performance) |

### Changing levels without restarting

Add to `application.properties`:
```properties
logging.level.org.aom.product=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

---

## 15. What You Should NOT Log

Payload logging is powerful but needs care. Some data must never appear in a log file.

| Data type | Example | Why not |
|-----------|---------|---------|
| Passwords | `{"password": "secret123"}` | Log files are often less protected than databases. Anyone who reads the log file gets the password. |
| Auth tokens / API keys | `Authorization: Bearer eyJ...` | A stolen token gives full access to the account it belongs to. |
| Credit card numbers | `{"cardNumber": "4111111111111111"}` | PCI-DSS is a legal standard for payment data. Logging card numbers can result in large fines and loss of the ability to take card payments. |
| Personal data (names, addresses, health info) | `{"email": "john@example.com"}` | GDPR (Europe) and similar laws regulate how personal data is stored. A log file counts as storage. |
| Large binary payloads | File uploads, images, PDFs | A single 5 MB file upload logged would fill 10 GB in a day under moderate load. |

**PCI-DSS** = Payment Card Industry Data Security Standard — the rules for handling card payment data.  
**GDPR** = General Data Protection Regulation — EU law on protecting personal information.

### How to handle this

The `PayloadLoggingFilter` already truncates bodies over 2000 characters.
For sensitive fields, you can sanitise before logging:

```java
private String sanitise(String body) {
    // Replace the value of any "password" field with ***
    return body.replaceAll(
        "\"password\"\\s*:\\s*\"[^\"]*\"",
        "\"password\":\"***\""
    );
}
```

**Rule of thumb:** if the data would be sensitive in a database, it is sensitive in a log file.

---

## 16. Sample Log Output

Here is what `logs/app.log` looks like after a few requests:

```
2024-08-16 14:32:01.123 [a3f7c2d1] [http-nio-8081-exec-1] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | POST /product-service/addProduct | Content-Type: application/json | Body: {"pName":"Google Pixel 9","skuCode":"GOO2024"}
2024-08-16 14:32:01.456 [a3f7c2d1] [http-nio-8081-exec-1] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 201 | Duration: 333ms | Body: {"prodNum":3,"skuCode":"GOO2024","pName":"Google Pixel 9"}

2024-08-16 14:32:05.001 [b9e1f4a2] [http-nio-8081-exec-2] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | GET /product-service/getAllProducts | Content-Type: none | Body: <empty>
2024-08-16 14:32:05.045 [b9e1f4a2] [http-nio-8081-exec-2] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 200 | Duration: 44ms | Body: [{"prodNum":1,...},{"prodNum":2,...},{"prodNum":3,...}]

2024-08-16 14:32:10.200 [c5d8e3b7] [http-nio-8081-exec-3] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | GET /product-service/getProduct/999 | Content-Type: none | Body: <empty>
2024-08-16 14:32:10.215 [c5d8e3b7] [http-nio-8081-exec-3] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 404 | Duration: 15ms | Body: {"timestamp":"...","status":404,"error":"Not Found","message":"Product not found with id: 999"}

2024-08-16 14:32:15.300 [d1a6f9c4] [http-nio-8081-exec-4] INFO  o.a.p.filter.PayloadLoggingFilter - >> INCOMING REQUEST  | POST /product-service/addProduct | Content-Type: application/json | Body: {"pName":"","skuCode":"GOO2024"}
2024-08-16 14:32:15.310 [d1a6f9c4] [http-nio-8081-exec-4] INFO  o.a.p.filter.PayloadLoggingFilter - << OUTGOING RESPONSE | Status: 400 | Duration: 10ms | Body: {"status":400,"error":"Bad Request","message":"pName: Product name must not be blank"}
```

Reading this output:
- `[a3f7c2d1]` — the requestId. Both lines for the same request share this.
- `[http-nio-8081-exec-1]` — the worker thread that handled this request.
- `>>` and `<<` — visual markers: `>>` = incoming, `<<` = outgoing.
- `Status: 201` — HTTP 201 Created — a product was successfully created.
- `Status: 404` — the product with id 999 was not found.
- `Status: 400` — the request was rejected because `pName` was blank.
- `Duration: 333ms` — the request took 333 milliseconds end to end.

---

## 17. Quick Reference

### Files in this project

| File | Purpose |
|------|---------|
| `src/main/java/.../filter/PayloadLoggingFilter.java` | The filter — logs every request and response |
| `src/main/resources/logback-spring.xml` | Logging configuration — format, destinations, levels |
| `docker-compose.yaml` | Volume mount `./logs:/app/logs` so logs persist on your Mac |
| `logs/.gitkeep` | Keeps the `logs/` folder in git (log files themselves are ignored) |

### Commands to watch logs

```bash
# Follow the log file in real time on your Mac
tail -f logs/app.log

# Follow Docker container output in real time
docker logs -f product_service_con

# Find all log lines for one request
grep "a3f7c2d1" logs/app.log

# Find all 404 errors
grep "Status: 404" logs/app.log

# Find all slow responses (500ms or more)
grep "OUTGOING RESPONSE" logs/app.log | grep -E "Duration: [5-9][0-9]{2}ms|Duration: [0-9]{4}"

# List log files (current + archives)
ls -lh logs/
```

### Key concepts summary

| Concept | What it is |
|---------|-----------|
| **Log** | A timestamped text record of something the application did |
| **Log file** | A plain text file where log entries are written |
| **Appender** | A destination for log output (console or file) |
| **Logger** | Controls the minimum level for a specific class or package |
| **Log level** | TRACE / DEBUG / INFO / WARN / ERROR — controls verbosity |
| **Servlet Filter** | Intercepts every HTTP request before it reaches any controller |
| **OncePerRequestFilter** | Spring base class — guarantees the filter runs exactly once per request |
| **InputStream** | A one-directional pipe — bytes can only flow through it once |
| **ContentCachingRequestWrapper** | Buffers the request body so it can be read without consuming the stream |
| **ContentCachingResponseWrapper** | Buffers the response body so it can be read before being sent to the client |
| **Thread** | A worker that handles one request from start to finish |
| **MDC** | Per-thread dictionary — stores the requestId so every log line is tagged |
| **Rolling policy** | Automatically rotates, compresses, and deletes old log files |
| **Volume mount** | A Docker feature connecting a folder on your Mac to a folder inside a container |

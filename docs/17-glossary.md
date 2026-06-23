# 17. Glossary

Every technical term in this project, explained simply.

---

**ACH (Automated Clearing House)**
An electronic money transfer network used in the United States for things like direct deposit and bill payments. Typically takes 1-3 business days to settle.

**API (Application Programming Interface)**
A way for programs to talk to each other. Like a waiter in a restaurant — you tell the waiter what you want (the API call), the waiter goes to the kitchen (the backend), and brings back your food (the response). You don't need to know how the kitchen works.

**API Gateway**
A single entry point for all requests. Like the front desk of a large company — everyone who comes in goes through it first. It validates identity, routes to the right department, and manages traffic.

**ArgoCD**
A tool that watches your Git repository and automatically updates Kubernetes to match what is in Git. If you change the configuration in Git, ArgoCD applies the change to the running system without any manual commands.

**Async (Asynchronous)**
Doing something without waiting for a reply. Like sending a text message — you send it and continue with your day. The other person reads it when they are ready. Contrast with synchronous.

**Audit Log**
A permanent, unchangeable record of every action that happened in the system. Who did what, when, from where. Required by banking regulations. Stored in Cassandra for immutability and fast time-based queries.

**Avro**
A compact data format for Kafka messages. Requires a schema (a contract that defines what the data looks like). Ensures producers and consumers agree on the data format.

**BCrypt**
A way to store passwords securely. Takes a password and turns it into a scrambled string (a hash). You cannot go backwards from the hash to the password. Even if someone steals the database, they cannot read the passwords.

**Bearer Token**
A type of authorization. "Bearer" means "whoever has this token is allowed in." The HTTP header looks like: `Authorization: Bearer eyJhbGci...`

**Cache**
Storing frequently-used data in a fast place (like Redis) so you don't have to fetch it from the slower original source (like a database) every time. Like keeping your most-used tools on your desk instead of in the storeroom.

**Cassandra**
A database designed for massive amounts of data that needs to be written very fast and read by a specific key. Used here for the audit log because it writes millions of records per day and queries are always "give me all events for user X."

**Circuit Breaker**
A pattern that stops calling a service that is not responding, to prevent cascading failures. Like an electrical circuit breaker in your house — when there is a problem, it trips and protects the whole system.

**CHIPS (Clearing House Interbank Payments System)**
A US payment network for large-value same-day payments between major banks.

**CloudFront**
Amazon's CDN (Content Delivery Network). Caches your website files in servers around the world. Users download the app from the nearest server instead of always from one location — making it faster.

**Consumer Group**
In Kafka, a group of service instances that share the work of reading messages. If there are 3 notification-service instances and 6 partitions, each instance reads from 2 partitions. No message is processed twice.

**CORS (Cross-Origin Resource Sharing)**
A browser security feature that prevents websites from making requests to a different domain without permission. Our banking app at app.bankingapp.com is explicitly allowed to call api.bankingapp.com. Random websites are not.

**CQRS (Command Query Responsibility Segregation)**
Separating the code that changes data (commands) from the code that reads data (queries). Reads and writes have different performance characteristics, so handling them separately allows for better optimization.

**Dead Letter Queue (DLQ)**
Where Kafka messages go after they have failed processing multiple times. Like a "problem mail" box. Operations staff investigate and replay messages after the bug is fixed.

**Docker**
A way to package an application and all its dependencies into a "container" — a self-contained box that runs the same way everywhere. Like shipping containers that hold goods — standard size, fits on any ship.

**ECR (Elastic Container Registry)**
Amazon's container image storage. Like DockerHub but private and integrated with AWS. CI/CD pushes images here; Kubernetes pulls from here.

**EKS (Elastic Kubernetes Service)**
Amazon's managed Kubernetes service. Amazon handles the Kubernetes control plane; you provide the worker nodes.

**Elasticsearch**
A search engine and database optimized for full-text search. Used here to store logs (indexed so you can search them by text, date, correlationId, etc.).

**EMI (Equated Monthly Installment)**
The fixed monthly payment on a loan. Calculated using the principal amount, interest rate, and tenure. The loan service calculates this automatically.

**Environment Variable**
A configuration value provided to a program from outside the code. Used for things like database passwords — they vary between environments (development vs production) and should not be in the source code.

**Eureka**
Netflix's service registry. Every service registers here when it starts. Other services look up addresses here. Like a live phonebook for services.

**Event Sourcing**
Storing every change to data as an event, rather than just the current state. The current state is calculated by replaying events. Provides a complete, auditable history. Used here for transactions — every debit and credit is stored.

**Feign Client**
A library that generates HTTP client code from a Java interface. You define the interface (what methods to call, what URLs they map to), and Feign generates the actual HTTP request code automatically.

**Flyway**
A database migration tool. Tracks which SQL scripts have been run against the database and runs only the new ones. Like version control for your database schema.

**Gitflow**
A branching strategy for Git. main = production. develop = integration. feature/* = new work. Changes flow: feature → develop → main.

**Grafana**
A dashboard and visualization tool. Connects to Prometheus (metrics), Elasticsearch (logs), Tempo (traces) and displays charts, graphs, and alerts.

**Helm**
A package manager for Kubernetes. Templates YAML files so you don't write repetitive configuration by hand. Like npm for Kubernetes.

**HPA (Horizontal Pod Autoscaler)**
A Kubernetes feature that automatically adjusts the number of pods running based on CPU or memory usage. If traffic spikes, more pods are created automatically.

**Idempotent**
An operation that produces the same result no matter how many times you perform it. Critical for message processing — if a Kafka message is delivered twice, processing it twice should not cause a double-charge.

**JWT (JSON Web Token)**
A signed string that proves who you are. Contains user ID, email, roles, and an expiry time. The signature (made with a secret key) proves it was issued by our server and has not been tampered with.

**Kafka**
A distributed message streaming platform. Services publish events (like "transaction completed") to topics, and other services subscribe to consume them. Highly scalable, messages are retained and replayable.

**KYC (Know Your Customer)**
A banking regulation requiring banks to verify the identity of their customers. Our user service handles KYC status: PENDING → SUBMITTED → VERIFIED → REJECTED.

**Kubernetes (K8s)**
An open-source system for managing containers. Runs containers on servers, restarts them if they crash, scales them based on traffic, and rolls out updates without downtime.

**Liveness Probe**
A health check Kubernetes uses to determine if a pod is running. If it fails repeatedly, Kubernetes restarts the pod.

**Logstash**
Part of the ELK stack. Collects logs, parses and enriches them (adds geo-IP, tags slow requests), and sends them to Elasticsearch.

**Lombok**
A Java library that generates boilerplate code (@Data generates getters/setters, @Builder generates builder pattern, @Slf4j generates a logger) via annotations.

**Mapstruct**
A Java library that generates code to convert between different object types (e.g., from a database entity to a DTO). Like an automatic translator between two data formats.

**Micrometer**
A metrics library for Java. Abstracts the metrics collection so you can switch from Prometheus to another system without changing your code. Spring Boot auto-configures it.

**MongoDB**
A NoSQL database that stores data as flexible JSON documents. Good when data has varying structure (like notifications, which can have different fields depending on type).

**MSK (Managed Streaming for Apache Kafka)**
Amazon's managed Kafka service. Amazon handles the Kafka infrastructure; you configure topics and connect your services.

**mTLS (Mutual TLS)**
A security protocol where both sides of a connection authenticate each other. Regular TLS: only the server proves its identity. mTLS: both client and server prove their identity.

**Offset**
In Kafka, the position of a consumer in a topic partition. Like a bookmark in a book. When a consumer restarts, it resumes from its last offset.

**Outbox Pattern**
A technique for reliably publishing messages to Kafka after saving to a database. The "intent to publish" is saved in the database atomically with the business data. A separate process polls and publishes. Guarantees no messages are lost even if the server crashes.

**Partition**
In Kafka, a topic is split into partitions. Messages in one partition are ordered. Multiple consumers can read from different partitions simultaneously for higher throughput.

**PDB (Pod Disruption Budget)**
A Kubernetes policy that limits how many pods can be taken down at once during planned maintenance. "Always keep at least 1 pod running" prevents downtime during cluster updates.

**PostgreSQL**
A powerful, open-source relational database. Used here for all structured data that needs ACID transactions (users, accounts, transactions, payments).

**Presigned URL**
A temporary URL for an S3 file that includes authentication information. Users can download the file directly from S3 using this URL without our servers being involved. The URL expires after a set time.

**Prometheus**
A monitoring system that collects metrics by polling services' /actuator/prometheus endpoints. Stores time-series data. Evaluated by alert rules.

**Readiness Probe**
A health check Kubernetes uses to determine if a pod is ready to receive traffic. A pod that is starting up will fail readiness checks until it is fully initialized. No traffic is sent until it passes.

**Redis**
An in-memory key-value store. Extremely fast (sub-millisecond) because data lives in RAM. Used here for JWT blacklists, refresh tokens, account caches, fraud velocity counters, and rate limiting.

**Resilience4j**
A Java library implementing resilience patterns: Circuit Breaker, Retry, Rate Limiter, Bulkhead, Time Limiter.

**Rolling Update**
A deployment strategy that updates pods one (or a few) at a time, rather than all at once. Traffic always has some pods available. Zero downtime.

**RTK Query**
Redux Toolkit Query — a data fetching and caching library built into Redux Toolkit. Handles loading states, error states, caching, cache invalidation, and refetching automatically.

**S3 (Simple Storage Service)**
Amazon's object storage. An infinite file system in the cloud. Stores any file (PDF statements, images, backups). Files are retrieved by key (like a filename).

**Saga**
A pattern for distributed transactions. Instead of one atomic transaction across multiple databases (impossible in microservices), a saga is a sequence of local transactions each publishing events to trigger the next step.

**Schema Registry**
A central repository for Avro schemas. Ensures Kafka producers and consumers agree on the data format. Producers register schemas before publishing. Consumers validate messages against registered schemas.

**SonarQube**
A code quality tool that analyzes source code for bugs, security vulnerabilities, and code smells. Reports test coverage. Used in the CI pipeline to enforce quality gates.

**Spring Batch**
A Spring framework for batch processing — processing large amounts of data in chunks. Used in the report service to generate statements for thousands of transactions without timeouts.

**Spring Boot**
A framework that makes it easy to create stand-alone Java applications. Handles configuration, server startup, dependency injection, and much more automatically.

**Spring Cloud**
Extensions to Spring Boot for distributed systems: Eureka client, Config client, Gateway, OpenFeign, CircuitBreaker, Load Balancer.

**Spring Security**
A framework for authentication and authorization in Spring applications. Handles JWT validation, role-based access control, CSRF protection, CORS.

**STOMP (Simple Text Oriented Messaging Protocol)**
A messaging protocol that works over WebSockets. Used for the real-time notification push in the notification service.

**SWIFT**
Society for Worldwide Interbank Financial Telecommunication. The network that banks use to send money internationally. MT103 is the message format for customer credit transfers.

**Sync (Synchronous)**
Doing something and waiting for the result before continuing. Like a phone call — you wait for the other person to answer and respond. Contrast with async.

**Tailwind CSS**
A CSS framework with pre-defined utility classes. Instead of writing custom CSS, you compose classes: `className="text-sm font-medium text-gray-900"`. No separate CSS files needed.

**Terraform**
Infrastructure as Code tool. Write configuration files describing AWS resources, run terraform apply, and the resources are created. Reproducible, version-controlled infrastructure.

**Testcontainers**
A Java library that starts real databases (PostgreSQL, Redis, Kafka) in Docker containers during tests. Integration tests run against real dependencies, not mocks.

**TLS (Transport Layer Security)**
The encryption protocol that makes HTTPS secure. Encrypts data in transit so intercepted network packets are unreadable gibberish.

**TypeScript**
JavaScript with type annotations. The compiler checks that you are using variables correctly (no passing a string where a number is expected). Catches bugs before runtime.

**UUID (Universally Unique Identifier)**
A 128-bit identifier that is statistically guaranteed to be unique across all systems and all time. Used as primary keys in our databases. Example: `550e8400-e29b-41d4-a716-446655440000`.

**VPC (Virtual Private Cloud)**
A private network in AWS. Resources inside the VPC can communicate with each other, but are isolated from the internet unless explicitly exposed.

**WebSocket**
A persistent, two-way connection between browser and server. Unlike HTTP (where the browser always initiates), WebSocket allows the server to PUSH data to the browser at any time. Used here for real-time notifications.

**Zod**
A TypeScript schema validation library. Define the shape of data (e.g., "email must be a valid email, password must be at least 8 characters") and validate user input against it. Used in React Hook Form.

**Zookeeper**
Apache Zookeeper — a coordination service used by Kafka for leader election and configuration management. Kafka 3.x is moving away from Zookeeper, but our setup still uses it.

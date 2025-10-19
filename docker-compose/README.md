# Docker Compose Configuration

This directory contains Docker Compose files for orchestrating the ecommerce microservices infrastructure.

## Files Overview

### **core.yml**
Base infrastructure services that all microservices depend on.

**Services:**
- **Zipkin** (Port 9411) - Distributed request tracing for monitoring inter-service communication
- **Service Discovery** (Port 8761) - Eureka server for dynamic service registration and discovery
- **Cloud Config** (Port 9296) - Centralized configuration management for all services

**Network:**
- All services run on `microservices_network` (named bridge network)
- Enables internal DNS resolution for service-to-service communication

**Environment Variables:**
- Services are configured with proper Eureka endpoints
- Zipkin integration for tracing
- Config server pointing to remote GitHub configuration repository

---

### **compose.yml**
Application microservices for the ecommerce business logic.

**Services:**

#### 1. **API Gateway** (Port 8080)
- Entry point for all external requests
- Routes requests to appropriate microservices
- Handles authentication and cross-cutting concerns
- Dependencies: Eureka, Config Server

#### 2. **User Service** (Port 8700)
- Manages user registration, profiles, and credentials
- Handles authentication and authorization
- Provides user data to other services
- Dependencies: Eureka, Config Server
- Features: Circuit breaker, H2 database, health checks

#### 3. **Product Service** (Port 8500)
- Manages product catalog and inventory
- Provides product information to orders and favorites
- Caching and performance optimization
- Dependencies: Eureka, Config Server
- Features: Circuit breaker, H2 database, health checks

#### 4. **Order Service** (Port 8300)
- Orchestrates order creation and management
- Communicates with User Service and Product Service
- Handles order workflow and status tracking
- Dependencies: Eureka, Config Server
- Features: Circuit breaker, H2 database, health checks
- Integration: Calls to user-service and product-service via Feign clients

---

## Network Architecture

```
┌─────────────────────────────────────────────────────┐
│         Docker Bridge Network                       │
│         microservices_network                       │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │         Core Services (core.yml)             │  │
│  │  ┌──────────────┐  ┌──────────────────────┐ │  │
│  │  │   Zipkin     │  │  Service Discovery   │ │  │
│  │  │  (Tracing)   │  │    (Eureka)          │ │  │
│  │  └──────────────┘  └──────────────────────┘ │  │
│  │           │              ▲                   │  │
│  │  ┌────────────────────────────────────────┐ │  │
│  │  │      Cloud Config Server               │ │  │
│  │  │    (Centralized Config)                │ │  │
│  │  └────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────┘  │
│           │                    │                   │
│  ┌────────┴────────────────────┴────────────────┐ │
│  │     Application Services (compose.yml)      │ │
│  │                                              │ │
│  │  ┌──────────────┐  ┌──────────────────────┐ │ │
│  │  │ API Gateway  │  │  User Service        │ │ │
│  │  │   :8080      │  │    :8700             │ │ │
│  │  └──────────────┘  └──────────────────────┘ │ │
│  │         │                    ▲              │ │
│  │  ┌──────────────────┬────────────────────┐ │ │
│  │  │  Product Service │  Order Service     │ │ │
│  │  │    :8500         │    :8300           │ │ │
│  │  └──────────────────┴────────────────────┘ │ │
│  └──────────────────────────────────────────────┘  │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## Service Startup Order

**Critical:** Services must start in this order due to dependencies:

1. **Core Services First** (from core.yml)
   - Zipkin boots immediately
   - Eureka starts and registers itself
   - Config Server starts and connects to Eureka
   - Wait ~50 seconds for initialization

2. **Application Services** (from compose.yml)
   - All 4 services can start in parallel
   - Services discover each other via Eureka
   - Wait ~30-60 seconds for full registration

---

## Environment Configuration

All services receive the following environment variables:

```yaml
SPRING_PROFILES_ACTIVE=dev
SPRING_ZIPKIN_BASE-URL=http://zipkin:9411
SPRING_CONFIG_IMPORT=optional:configserver:http://cloud-config-container:9296/
EUREKA_CLIENT_REGION=default
EUREKA_CLIENT_AVAILABILITYZONES_DEFAULT=myzone
EUREKA_CLIENT_SERVICEURL_MYZONE=http://service-discovery-container:8761/eureka
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-discovery-container:8761/eureka/
```

---

## Service Communication

Services communicate via:

1. **Synchronous (Feign Clients):**
   - Order Service → User Service
   - Order Service → Product Service
   - API Gateway → Application Services

2. **Asynchronous (via Eureka discovery):**
   - All services discover each other dynamically
   - Service endpoints resolved at runtime

3. **Tracing (Zipkin):**
   - All requests traced across service boundaries
   - Visible at http://localhost:9411

---

## Managing Services

### Start
```bash
docker compose -f core.yml up -d
sleep 50
docker compose -f compose.yml up -d
```

### Stop
```bash
docker compose -f compose.yml down
docker compose -f core.yml down
```

### View Logs
```bash
docker compose -f core.yml logs -f service-discovery-container
docker compose -f compose.yml logs -f order-service-container
```

### Restart Single Service
```bash
docker compose -f compose.yml restart user-service-container
```

---

## Health Verification

### Check Eureka Registration
```bash
curl -s http://localhost:8761/eureka/apps | grep -oP '(?<=<app>)[^<]+'
```

Expected output:
```
API-GATEWAY
CLOUD-CONFIG
ORDER-SERVICE
PRODUCT-SERVICE
USER-SERVICE
```

### Check Service Health
```bash
curl http://localhost:8700/user-service/actuator/health
curl http://localhost:8500/product-service/actuator/health
curl http://localhost:8300/order-service/actuator/health
```

---

## Customization

### Change Ports
Edit `core.yml` or `compose.yml` and modify port mappings:
```yaml
ports:
  - "EXTERNAL_PORT:INTERNAL_PORT"
```

### Change Images
Update image references to use different versions:
```yaml
image: selimhorri/user-service-ecommerce-boot:CUSTOM_VERSION
```

### Add New Services
Add new service block following the existing pattern:
```yaml
new-service-container:
  image: your/image:tag
  ports:
    - "PORT:PORT"
  networks:
    - microservices_network
  environment:
    - SPRING_PROFILES_ACTIVE=dev
    # ... other env vars
```

---

## Notes

- Network persistence: The `microservices_network` persists even if containers are stopped
- Data persistence: Services use H2 in-memory databases (not persistent)
- For production: Replace with managed databases (PostgreSQL, MySQL, etc.)
- SSL/TLS: Currently disabled; add certificates for production deployments


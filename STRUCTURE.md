# Ecommerce Microservices - Project Structure

## Directory Organization

This document explains the organized structure of the ecommerce microservices project after the DevOps improvements.

---

## Project Layout

```
ecommerce-microservice-backend-app/
│
├── docker-compose/                  ← Docker Compose Configuration Package
│   ├── core.yml                        ← Base infrastructure services
│   ├── compose.yml                     ← Application microservices
│   └── README.md                       ← Docker Compose documentation
│
├── scripts/                         ← Orchestration Scripts Package
│   ├── start-services.sh               ← Start all services (with proper order)
│   ├── validate-services.sh            ← Health check all services
│   ├── stop-services.sh                ← Stop all services cleanly
│   └── README.md                       ← Scripts documentation and usage guide
│
├── [microservices]/                 ← Individual service modules
│   ├── api-gateway/                    ← Spring Cloud Gateway
│   ├── user-service/                   ← User management service
│   ├── product-service/                ← Product catalog service
│   ├── order-service/                  ← Order orchestration service
│   ├── cloud-config/                   ← Centralized configuration service
│   └── service-discovery/              ← Eureka service discovery
│
├── pom.xml                             ← Maven parent POM (Java 11, Lombok 1.18.30)
├── README.md                           ← Main project documentation
└── STRUCTURE.md                        ← This file

```

---

## Package Purposes

### **`docker-compose/` Package**
**Purpose:** Centralized Docker Compose configuration for entire infrastructure

**Contains:**
- `core.yml` - Base infrastructure (Eureka 8761, Config Server 9296, Zipkin 9411)
- `compose.yml` - Application services (API Gateway 8080, 4 microservices)
- `README.md` - Complete Docker Compose documentation

**Usage:**
```bash
cd docker-compose
docker compose -f core.yml up -d
docker compose -f compose.yml up -d
```

**Benefits:**
- Single location for all Docker Compose configs
- Organized and easy to maintain
- Clear separation of infrastructure vs application services
- Easy to extend with new services

---

### **`scripts/` Package**
**Purpose:** Automation scripts for managing microservices lifecycle

**Contains:**
- `start-services.sh` - Orchestrates complete startup with proper initialization order
- `validate-services.sh` - Comprehensive health checks for all services
- `stop-services.sh` - Clean shutdown of all services
- `README.md` - Detailed documentation with examples and troubleshooting

**Usage:**
```bash
cd scripts
./start-services.sh        # Start all services
./validate-services.sh     # Verify all healthy
./stop-services.sh         # Stop all services
```

**Benefits:**
- Reliable service orchestration
- Proper wait times between service startup phases
- Automated health validation
- Color-coded output for easy monitoring
- Debugging tips included

---

## Service Architecture Overview

### **Infrastructure Services (core.yml)**
```
┌─────────────────────────────────────┐
│    Docker Bridge Network             │
│    (microservices_network)           │
│                                     │
│  Zipkin (9411)                      │
│  ↓                                  │
│  Service Discovery / Eureka (8761)  │
│  ↓                                  │
│  Cloud Config Server (9296)         │
└─────────────────────────────────────┘
```

**Services:**
1. **Zipkin** - Distributed request tracing
2. **Eureka** - Dynamic service discovery and registration
3. **Cloud Config** - Centralized configuration management

**Purpose:** Provides foundation for all microservices

---

### **Application Services (compose.yml)**
```
┌─────────────────────────────────────┐
│    Docker Bridge Network             │
│    (microservices_network)           │
│                                     │
│  API Gateway (8080) ──→ Routes      │
│       ↓                             │
│  ┌─────────────────────────────┐   │
│  │  User Service (8700)        │   │
│  │  Product Service (8500)     │   │
│  │  Order Service (8300)       │   │
│  │  Payment Service (optional) │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Services:**
1. **API Gateway** - Entry point, request routing
2. **User Service** - User management and authentication
3. **Product Service** - Product catalog
4. **Order Service** - Order orchestration
5. **Payment Service** (optional) - Payment processing

**Purpose:** Business logic microservices, all discoverable via Eureka

---

## Quick Start Workflow

### **Step 1: Start Services**
```bash
./scripts/start-services.sh
```
This will:
- Start infrastructure services (Eureka, Config, Zipkin)
- Wait for initialization
- Start application services
- Display access URLs

### **Step 2: Validate Services**
```bash
./scripts/validate-services.sh
```
This will:
- Test all 7 services
- Check Eureka registration
- Validate business endpoints
- Report any issues

### **Step 3: Access Services**
```bash
# API Gateway
curl http://localhost:8080/app/api/users

# Eureka Dashboard
open http://localhost:8761

# Zipkin Tracing
open http://localhost:9411

# Config Server
curl http://localhost:9296/actuator/health
```

### **Step 4: Stop Services**
```bash
./scripts/stop-services.sh
```

---

## Key Technologies

### **Build System**
- Maven 3.8.1+ (wrapper included)
- Java 11 target
- Spring Boot 2.5.7

### **Service Framework**
- Spring Cloud (Eureka, Config Server, Gateway, Feign)
- Spring Boot Actuator (Health checks)
- Resilience4j (Circuit breakers)

### **Containerization**
- Docker 28.5.1+
- Docker Compose v2.39+
- Named bridge network for service discovery

### **Monitoring & Tracing**
- Eureka Server (Service discovery)
- Zipkin (Distributed tracing)
- Spring Cloud Config (Centralized configuration)

### **Dependencies**
- Lombok 1.18.30 (Java 11+ compatible)
- Spring Boot 2.5.7
- H2 Database (embedded)

---

## Configuration Management

### **Spring Profiles**
- `dev` - Development profile (active by default)
- `stage` - Staging profile
- `prod` - Production profile

### **Environment Variables**
All services receive:
```yaml
SPRING_PROFILES_ACTIVE=dev
SPRING_ZIPKIN_BASE_URL=http://zipkin:9411
SPRING_CONFIG_IMPORT=optional:configserver:http://cloud-config-container:9296/
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-discovery-container:8761/eureka/
```

### **Network Configuration**
- Network: `microservices_network` (Docker bridge)
- Service DNS: Services resolve by container name
- Inter-service communication: Via Eureka discovery

---

## Verification Checklist

After starting services, verify:

- [ ] All 7 containers running: `docker ps | grep "ecommerce\|zipkin"`
- [ ] Eureka showing 5 registered services: `curl http://localhost:8761/eureka/apps`
- [ ] API Gateway responding: `curl http://localhost:8080/app/actuator/health`
- [ ] User Service responding: `curl http://localhost:8700/user-service/actuator/health`
- [ ] Product Service responding: `curl http://localhost:8500/product-service/actuator/health`
- [ ] Order Service responding: `curl http://localhost:8300/order-service/actuator/health`
- [ ] Zipkin collecting traces: `curl http://localhost:9411/api/v1/services`
- [ ] Config Server responding: `curl http://localhost:9296/actuator/health`

---

## Improvements Made

### **Build System**
- Fixed Lombok + Java 11 compatibility
- Updated Maven Compiler Plugin to 3.11.0
- All 11 microservices compile successfully
- Explicit version management for dependencies

### **Containerization**
- Created organized docker-compose package
- Separated infrastructure and application services
- Proper service initialization order with waits
- Named Docker network for service discovery

### **Automation**
- Created comprehensive orchestration scripts
- Automated health checks with validation script
- Clean service shutdown procedures
- Color-coded output for monitoring

### **Documentation**
- Detailed Docker Compose documentation
- Comprehensive scripts usage guide
- Architecture diagrams included
- Troubleshooting section provided
- Quick start examples included

---

## Workshop Requirements Coverage

**Phase 1 - Development Environment:** Complete
- Docker Compose infrastructure running
- All 6 microservices operational
- Service discovery and configuration working
- Distributed tracing enabled

**Phase 2 - Testing:** In Progress
- Unit tests needed (5+ per service)
- Integration tests needed (5+)
- E2E tests needed (5+)
- Performance testing with Locust

**Phase 3 - CI/CD Pipelines:** Planned
- Jenkins pipeline configuration
- GitHub Actions workflows
- Build automation
- Automated testing

**Phase 4 - Kubernetes Deployment:** Planned
- Kubernetes manifests
- Helm charts (optional)
- Dev/Staging/Production environments
- Resource management and scaling

---

## File Manifest

| File | Purpose | Type |
|------|---------|------|
| `docker-compose/core.yml` | Infrastructure services | Config |
| `docker-compose/compose.yml` | Application services | Config |
| `docker-compose/README.md` | Docker Compose docs | Doc |
| `scripts/start-services.sh` | Start all services | Script |
| `scripts/validate-services.sh` | Health checks | Script |
| `scripts/stop-services.sh` | Stop all services | Script |
| `scripts/README.md` | Scripts documentation | Doc |
| `pom.xml` | Maven parent config | Config |

---

## Reference Documentation

- **Docker Compose Docs:** See `docker-compose/README.md`
- **Scripts Documentation:** See `scripts/README.md`
- **Main Project README:** See `README.md`
- **Maven Configuration:** See `pom.xml`

---

## Best Practices Implemented

1. **Separation of Concerns** - Infrastructure and app services separated
2. **Automation** - Scripts handle repetitive tasks
3. **Documentation** - Comprehensive guides included
4. **Health Checks** - Validation script ensures system health
5. **Proper Ordering** - Services start in correct dependency order
6. **Network Isolation** - Named bridge network for service communication
7. **Dependency Management** - Explicit version pinning for compatibility
8. **Error Handling** - Scripts include exit codes and debugging tips

---

## Next Steps

1. **Run services:** `cd scripts && ./start-services.sh`
2. **Validate:** `./validate-services.sh`
3. **Explore:** Visit `http://localhost:8761` for Eureka dashboard
4. **Test APIs:** Use curl or Postman to test endpoints
5. **View traces:** Visit `http://localhost:9411` for Zipkin traces
6. **Stop services:** `./stop-services.sh`

---

**Last Updated:** October 19, 2025
**Status:** Development Environment Complete


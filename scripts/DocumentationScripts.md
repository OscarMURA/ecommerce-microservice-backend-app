# Docker Compose Scripts Documentation

This directory contains utility scripts for managing the ecommerce microservices infrastructure using Docker Compose.

##  Available Scripts

### 1. **start-services.sh**
Automatically starts all microservices in the correct order, including base services (Eureka, Config Server, Zipkin) and the 6 main application services.

**Usage:**
```bash
./start-services.sh
```

**What it does:**
- Starts core services from `core.yml` (Eureka, Config Server, Zipkin)
- Waits 50 seconds for initialization
- Verifies Eureka is available
- Starts 6 main application services from `compose.yml`:
  - API Gateway (port 8080)
  - User Service (port 8700)
  - Product Service (port 8500)
  - Order Service (port 8300)
- Waits 60 seconds for service registration
- Displays registered services and accessible URLs

**Output includes:**
- Status of each service initialization phase
- List of registered services in Eureka
- Quick access URLs for testing

---

### 2. **validate-services.sh**
Validates that all services are running and responding correctly. Performs health checks on all endpoints and verifies service registration in Eureka.

**Usage:**
```bash
./validate-services.sh
```

**What it does:**
- Checks HTTP 200 response from:
  - Eureka (http://localhost:8761/eureka/apps)
  - Config Server (http://localhost:9296/actuator/health)
  - Zipkin (http://localhost:9411)
  - API Gateway (http://localhost:8080/app/actuator/health)
  - User Service (http://localhost:8700/user-service/actuator/health)
  - Product Service (http://localhost:8500/product-service/actuator/health)
  - Order Service (http://localhost:8300/order-service/actuator/health)
- Tests business endpoints:
  - GET /users
  - GET /products
  - GET /orders
- Shows list of services registered in Eureka
- Provides diagnostic information if services are down

**Exit code:** 
- 0 = All tests passed
- 1 = Some tests failed

---

### 3. **stop-services.sh**
Cleanly shuts down all running containers and Docker Compose services.

**Usage:**
```bash
./stop-services.sh
```

**What it does:**
- Stops all services defined in `compose.yml`
- Stops all services defined in `core.yml`
- Displays remaining running containers related to the project
- Optional: can remove volumes using `-v` flag

**Notes:**
- Containers are preserved (not deleted) unless explicitly removed
- To also remove volumes: `docker compose -f core.yml down -v`

---

##  Quick Start Guide

### 1. Start All Services
```bash
./start-services.sh
```

### 2. Validate Everything Works
```bash
./validate-services.sh
```

### 3. Access Services
- **Eureka Dashboard:** http://localhost:8761
- **Zipkin Tracing:** http://localhost:9411
- **User Service:** http://localhost:8700/user-service/api/users
- **Product Service:** http://localhost:8500/product-service/api/products
- **Order Service:** http://localhost:8300/order-service/api/orders

### 4. Stop Everything
```bash
./stop-services.sh
```

---

## Service Architecture

The scripts manage two Docker Compose files:

### **core.yml** (Base Infrastructure)
- Zipkin (Distributed Tracing)
- Service Discovery (Eureka)
- Cloud Config Server

### **compose.yml** (Application Services)
- API Gateway
- User Service
- Product Service
- Order Service

All services communicate through a shared Docker bridge network: `microservices_network`

---

##  Troubleshooting

### Services not starting?
1. Check Docker daemon is running: `docker ps`
2. Review logs: `docker logs <container_name>`
3. Increase wait times in scripts if services are slow to initialize

### Health checks failing?
- Wait longer before running `validate-services.sh` (services may still be initializing)
- Check network connectivity: `docker network inspect microservices_network`

### Port conflicts?
Edit `compose.yml` and `core.yml` to use different ports if:
- Port 8080 (API Gateway)
- Port 8700 (User Service)
- Port 8500 (Product Service)
- Port 8300 (Order Service)
- Port 8761 (Eureka)
- Port 9296 (Config Server)
- Port 9411 (Zipkin)

---

##  Dependencies

- Docker (version 20.10+)
- Docker Compose (v2+)
- bash shell
- curl (for validation)

---

##  Notes

- Scripts should be run from the project root directory
- All scripts are POSIX-compliant bash
- Service startup order is important; do not modify start/stop sequence
- For production deployments, consider using Kubernetes manifests instead


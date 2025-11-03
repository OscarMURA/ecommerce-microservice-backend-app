# Resumen de Pruebas E2E (End-to-End)

## Visión General

Se implementaron **5 pruebas E2E** que validan flujos completos de usuario a través de múltiples microservicios del sistema ecommerce. Estas pruebas simulan el comportamiento real de un usuario navegando por la aplicación web completa.

**Total: 5/5 pruebas E2E pasando (100% éxito)**

---

## Arquitectura de Pruebas E2E

### Stack Tecnológico
- **Framework**: Spring Boot Test (`@SpringBootTest`)
- **Cliente HTTP**: TestRestTemplate
- **Base de Datos**: H2 In-Memory Database
- **Serialización**: Jackson ObjectMapper
- **Patrón de Testing**: AAA (Arrange-Act-Assert)
- **Orden de Ejecución**: `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`

### Configuración
```properties
spring.application.name=e2e-tests
spring.datasource.url=jdbc:h2:mem:testdb_e2e
spring.jpa.hibernate.ddl-auto=create-drop
spring.cloud.service-registry.auto-registration.enabled=false
eureka.client.enabled=false
```

**Key Features:**
- H2 database aislada para pruebas E2E
- Service Discovery deshabilitado para pruebas independientes
- TestRestTemplate para comunicación HTTP real
- Orden de ejecución controlado con `@Order`
- Configuración de logging para debugging

---

## Pruebas E2E Implementadas

### **E2E Test 1: Complete User Registration and Profile Setup**
**Archivo**: `/e2e-tests/src/test/java/com/selimhorri/app/e2e/E2EUserJourneyTest.java`

```
Objetivo: Validar flujo completo de registro de usuario
Flujo: Registro → Credenciales → Dirección → Verificación de perfil
Servicios: user-service, credential-service, address-service
Patrón: Create → Link → Verify → Assert

PASSING
```

**Pasos del Flujo:**
1. **ARRANGE**: Preparar datos de usuario (nombre, email, teléfono)
2. **ACT 1**: Crear usuario via POST `/api/users`
3. **ASSERT 1**: Verificar creación exitosa y obtención de ID
4. **ACT 2**: Crear credenciales via POST `/api/credentials`
5. **ASSERT 2**: Verificar asociación de credenciales
6. **ACT 3**: Agregar dirección via POST `/api/addresses`
7. **ASSERT 3**: Verificar agregado de dirección
8. **ACT 4**: Recuperar perfil completo via GET `/api/users/{id}`
9. **ASSERT 4**: Verificar perfil completo con todos los datos

**Validaciones:**
- Creación exitosa de usuario con ID generado
- Asociación correcta de credenciales
- Agregado exitoso de dirección
- Recuperación de perfil completo con todos los datos

---

### **E2E Test 2: Product Catalog Browsing and Category Management**
**Archivo**: `/e2e-tests/src/test/java/com/selimhorri/app/e2e/E2EUserJourneyTest.java`

```
Objetivo: Validar flujo de navegación del catálogo de productos
Flujo: Crear categoría → Crear productos → Navegar catálogo
Servicios: product-service, category-service
Patrón: Create Category → Create Products → Browse → Assert

PASSING
```

**Pasos del Flujo:**
1. **ARRANGE**: Preparar datos de categoría y productos
2. **ACT 1**: Crear categoría via POST `/api/categories`
3. **ASSERT 1**: Verificar creación de categoría
4. **ACT 2**: Crear productos en la categoría via POST `/api/products`
5. **ASSERT 2**: Verificar creación de productos
6. **ACT 3**: Navegar catálogo via GET `/api/products`
7. **ASSERT 3**: Verificar navegación del catálogo

**Validaciones:**
- Creación exitosa de categoría
- Creación de productos con asociación a categoría
- Navegación del catálogo de productos
- Relaciones ManyToOne funcionando correctamente

---

### **E2E Test 3: Complete Order Creation and Management Flow**
**Archivo**: `/e2e-tests/src/test/java/com/selimhorri/app/e2e/E2EUserJourneyTest.java`

```
Objetivo: Validar flujo completo de creación y gestión de órdenes
Flujo: Crear orden → Agregar items → Procesar pago → Verificar estado
Servicios: order-service, shipping-service, payment-service
Patrón: Create Order → Add Items → Process Payment → Verify

PASSING
```

**Pasos del Flujo:**
1. **ARRANGE**: Preparar datos de orden, items y pago
2. **ACT 1**: Crear orden via POST `/api/orders`
3. **ASSERT 1**: Verificar creación de orden
4. **ACT 2**: Crear item de orden via POST `/api/order-items`
5. **ASSERT 2**: Verificar creación de item
6. **ACT 3**: Procesar pago via POST `/api/payments`
7. **ASSERT 3**: Verificar procesamiento de pago
8. **ACT 4**: Recuperar detalles de orden via GET `/api/orders/{id}`
9. **ASSERT 4**: Verificar estado de orden

**Validaciones:**
- Creación exitosa de orden
- Agregado de items de orden con claves compuestas
- Procesamiento de pago con estados correctos
- Verificación de estado de orden

---

### **E2E Test 4: Favorites Management and User Preferences**
**Archivo**: `/e2e-tests/src/test/java/com/selimhorri/app/e2e/E2EUserJourneyTest.java`

```
Objetivo: Validar flujo de gestión de favoritos del usuario
Flujo: Agregar favorito → Ver favoritos → Eliminar favorito
Servicios: favourite-service, user-service, product-service
Patrón: Add Favourite → Browse Favourites → Remove → Assert

PASSING
```

**Pasos del Flujo:**
1. **ARRANGE**: Preparar datos de favorito
2. **ACT 1**: Agregar producto a favoritos via POST `/api/favourites`
3. **ASSERT 1**: Verificar agregado a favoritos
4. **ACT 2**: Recuperar favoritos del usuario via GET `/api/favourites`
5. **ASSERT 2**: Verificar recuperación de favoritos
6. **ACT 3**: Recuperar favorito específico via GET `/api/favourites/{userId}/{productId}/{likeDate}`
7. **ASSERT 3**: Verificar recuperación específica

**Validaciones:**
- Agregado exitoso a favoritos
- Recuperación de lista de favoritos
- Recuperación de favorito específico por clave compuesta
- Manejo correcto de LocalDateTime en claves compuestas

---

### **E2E Test 5: Complete E-commerce Transaction Flow**
**Archivo**: `/e2e-tests/src/test/java/com/selimhorri/app/e2e/E2EUserJourneyTest.java`

```
Objetivo: Validar flujo completo de transacción ecommerce
Flujo: Autenticación → Navegación → Compra → Verificación → Limpieza
Servicios: Todos los microservicios
Patrón: Auth → Browse → Purchase → Verify → Cleanup

PASSING
```

**Pasos del Flujo:**
1. **ARRANGE**: Usar datos creados en pruebas anteriores
2. **ACT 1**: Autenticación de usuario via GET `/api/users/{id}`
3. **ASSERT 1**: Verificar autenticación
4. **ACT 2**: Navegación de productos via GET `/api/products/{id}`
5. **ASSERT 2**: Verificar navegación
6. **ACT 3**: Verificación de estado de orden via GET `/api/orders/{id}`
7. **ASSERT 3**: Verificar estado de orden
8. **ACT 4**: Verificación de estado de pago via GET `/api/payments`
9. **ASSERT 4**: Verificar estado de pago
10. **ACT 5**: Verificación de items de envío via GET `/api/order-items`
11. **ASSERT 5**: Verificar items de envío
12. **ACT 6**: Limpieza de favoritos via DELETE `/api/favourites/{userId}/{productId}/{likeDate}`
13. **ASSERT 6**: Verificar limpieza

**Validaciones:**
- Flujo completo de autenticación
- Navegación de productos
- Verificación de estados de orden y pago
- Verificación de items de envío
- Limpieza de datos

---

## Comparación: Unitarias vs Integración vs E2E

| Aspecto | Unitarias | Integración | E2E |
|---------|-----------|-------------|-----|
| **Scope** | Método individual | Service→Repository→DB | Flujo completo de usuario |
| **Dependencias** | Mocked | Reales (H2 DB) | Reales (HTTP + H2 DB) |
| **Velocidad** | Rápida (<1s) | Media (2-3s) | Lenta (5-10s) |
| **Total en Proyecto** | 49 | 30 | 5 |
| **Framework** | JUnit 5 + Mockito | @SpringBootTest + H2 | @SpringBootTest + TestRestTemplate |
| **Comunicación** | N/A | Repository Layer | HTTP REST APIs |
| **Cobertura** | Lógica de negocio | Persistencia | Flujos de usuario |

---

## Métricas de Ejecución

### **E2E Tests Execution**
```
Tests run: 5
Failures: 0
Errors: 0
Skipped: 0
Time elapsed: 45.2 s
BUILD SUCCESS
```

### **Breakdown por Test**
```
E2E Test 1 (User Registration): 8.5s
E2E Test 2 (Product Catalog): 7.2s
E2E Test 3 (Order Management): 12.1s
E2E Test 4 (Favorites): 9.8s
E2E Test 5 (Complete Transaction): 7.6s
```

### **Total del Proyecto**
```
Unit Tests: 49/49 (100%)
Integration Tests: 30/30 (100%)
E2E Tests: 5/5 (100%)
TOTAL TESTS: 84/84 (100% - ALL PASSING)
```

---

## Patrones Aplicados

### **1. Orden de Ejecución Controlado**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Test
@Order(1)
void testUserRegistration() { ... }
```

**Ventajas:**
- Tests se ejecutan en secuencia lógica
- Datos de tests anteriores disponibles para tests posteriores
- Simula flujo real de usuario

### **2. TestRestTemplate para Comunicación HTTP**
```java
@Autowired
private TestRestTemplate restTemplate;

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<Dto> request = new HttpEntity<>(dto, headers);

ResponseEntity<Dto> response = restTemplate.exchange(
    baseUrl + "/api/endpoint",
    HttpMethod.POST,
    request,
    Dto.class
);
```

**Ventajas:**
- Comunicación HTTP real entre servicios
- Validación de endpoints REST
- Prueba de serialización/deserialización JSON

### **3. Configuración Aislada**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
```

**Ventajas:**
- Puerto aleatorio evita conflictos
- Perfil de test aislado
- Base de datos H2 independiente

---

## Issues Encontrados y Resueltos

### **Issue 1: Dependencias entre Tests**
**Síntoma**: Tests fallan cuando se ejecutan individualmente
**Causa**: Tests dependen de datos creados en tests anteriores
**Solución**: Usar `@TestMethodOrder` y `@Order` para controlar secuencia
**Resultado**: Resuelto

### **Issue 2: Serialización de LocalDateTime**
**Síntoma**: Error al serializar LocalDateTime en claves compuestas
**Causa**: Formato de fecha no coincide entre cliente y servidor
**Solución**: Usar `DateTimeFormatter` consistente
**Resultado**: Resuelto

### **Issue 3: Timeout en Comunicación HTTP**
**Síntoma**: Tests fallan por timeout
**Causa**: Configuración de timeout muy baja
**Solución**: Configurar `test.e2e.timeout=30000`
**Resultado**: Resuelto

---

## Estructura de Archivos

```
e2e-tests/
├── pom.xml                                    ← Maven configuration
├── README.md                                  ← Documentation
├── src/test/
│   ├── java/com/selimhorri/app/e2e/
│   │   └── E2EUserJourneyTest.java           ← 5 E2E tests
│   └── resources/
│       ├── application.properties             ← Test configuration
│       └── application-test.yml              ← YAML configuration
└── target/
    └── surefire-reports/                      ← Test reports
```

---

## Ejecución Local de Pruebas E2E

### **Prerrequisitos**
- **Java 11+** instalado y configurado
- **Maven 3.6+** o usar el wrapper incluido (`./mvnw`)
- **Git** para clonar el repositorio
- **Puerto 8080** disponible (para API Gateway)

### **Configuración del Entorno Local**

#### **1. Clonar el Repositorio**
```bash
git clone https://github.com/OscarMURA/ecommerce-microservice-backend-app.git
cd ecommerce-microservice-backend-app
```

#### **2. Verificar Estructura del Proyecto**
```bash
# Verificar que el módulo e2e-tests existe
ls -la e2e-tests/

# Verificar configuración Maven
cat e2e-tests/pom.xml | grep -A 5 -B 5 "artifactId"
```

#### **3. Configurar Variables de Entorno (Opcional)**
```bash
# Para debugging avanzado
export SPRING_PROFILES_ACTIVE=test
export LOGGING_LEVEL_COM_SELIMHORRI_APP=DEBUG

# Para timeout personalizado
export TEST_E2E_TIMEOUT=30000
```

### **Ejecución de Pruebas E2E**

#### **Opción 1: Ejecutar Todas las Pruebas E2E**
```bash
# Desde la raíz del proyecto
cd e2e-tests
./mvnw clean test -Dtest=*E2E*Test

# O usando el wrapper desde la raíz
./mvnw -pl e2e-tests clean test -Dtest=*E2E*Test
```

#### **Opción 2: Ejecutar Prueba Específica**
```bash
# Ejecutar solo el test de registro de usuario
./mvnw test -Dtest=E2EUserJourneyTest#testCompleteUserRegistrationFlow

# Ejecutar solo el test de catálogo de productos
./mvnw test -Dtest=E2EUserJourneyTest#testProductCatalogBrowsingFlow
```

#### **Opción 3: Ejecutar con Configuración Específica**
```bash
# Con perfil de test explícito
./mvnw test -Dspring.profiles.active=test

# Con logging detallado
./mvnw test -Dlogging.level.com.selimhorri.app=DEBUG

# Con timeout personalizado
./mvnw test -Dtest.e2e.timeout=60000
```

#### **Opción 4: Ejecutar con Reportes Detallados**
```bash
# Generar reportes HTML
./mvnw test -Dtest=*E2E*Test -Dsurefire.reportFormat=html

# Ver reportes generados
open target/surefire-reports/index.html
```

### **Verificación de Resultados**

#### **1. Verificar Ejecución Exitosa**
```bash
# Buscar mensaje de éxito en logs
grep -i "BUILD SUCCESS" target/surefire-reports/*.txt

# Ver resumen de pruebas
grep "Tests run:" target/surefire-reports/*.txt
```

#### **2. Revisar Reportes Generados**
```bash
# Listar archivos de reporte
ls -la target/surefire-reports/

# Ver reporte XML detallado
cat target/surefire-reports/TEST-*E2E*Test.xml
```

#### **3. Debugging de Fallos**
```bash
# Ver logs detallados
tail -f target/surefire-reports/*.txt

# Verificar configuración de base de datos
grep -i "h2" target/surefire-reports/*.txt
```

### **Comandos de Utilidad**

#### **Limpiar y Reconstruir**
```bash
# Limpiar artefactos anteriores
./mvnw clean

# Compilar sin ejecutar tests
./mvnw compile -DskipTests

# Ejecutar solo compilación de e2e-tests
./mvnw -pl e2e-tests compile
```

#### **Verificar Dependencias**
```bash
# Ver dependencias del módulo e2e-tests
./mvnw -pl e2e-tests dependency:tree

# Verificar que H2 está disponible
./mvnw -pl e2e-tests dependency:resolve | grep h2
```

---

## Pipeline de CI/CD

### **GitHub Actions Workflow**
**Archivo**: `.github/workflows/e2e-test.yml`

#### **Configuración del Workflow**
```yaml
# Triggers del workflow
on:
  push:
    branches: [dev, develop, 'feat/**', 'test/**']
  pull_request:
    branches: [dev, develop, 'feat/**', 'test/**']
```

#### **Jobs del Pipeline**
1. **e2e-test**: Ejecuta las pruebas E2E
2. **e2e-test-summary**: Genera resumen y comentarios

#### **Flujo Detallado**
1. **Checkout**: Descarga el código del repositorio
2. **Setup JDK 11**: Configura Java 11 con Temurin
3. **Cache Maven**: Cachea dependencias Maven para velocidad
4. **Run E2E Tests**: Ejecuta `./mvnw clean test -Dtest=*E2E*Test -q`
5. **Upload Results**: Sube artefactos de resultados
6. **Publish Results**: Publica resultados en GitHub
7. **Generate Summary**: Crea resumen y comenta en PR

#### **Configuración de Entorno**
```yaml
# Configuración del job principal
jobs:
  e2e-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
```

#### **Artefactos Generados**
- **Test Reports**: `e2e-tests/target/surefire-reports/`
- **Test Summary**: Reporte markdown con resultados
- **PR Comments**: Comentarios automáticos en Pull Requests

### **Jenkins Pipeline**
**Archivo**: `jenkins/Jenkins_Dev.groovy`

#### **Configuración del Pipeline**
```groovy
pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  
  parameters {
    string(name: 'VM_NAME', defaultValue: 'ecommerce-integration-runner')
    string(name: 'VM_REGION', defaultValue: 'nyc3')
    // ... más parámetros
  }
}
```

#### **Stages del Pipeline Jenkins**
1. **Validate Branch**: Valida que la rama sea `develop` o `feat/**`
2. **Checkout Pipeline Repo**: Descarga el código del pipeline
3. **Ensure VM Available**: Verifica/crea VM en DigitalOcean
4. **Sync Repository on VM**: Sincroniza código en la VM
5. **Unit Tests**: Ejecuta pruebas unitarias
6. **Integration Tests**: Ejecuta pruebas de integración
7. **E2E Tests**: Ejecuta pruebas E2E
8. **Recolectar Reportes**: Recopila todos los reportes
9. **Build and Push Docker Images**: Construye imágenes Docker (opcional)
10. **Deploy to Kubernetes**: Despliega en K8s (opcional)

#### **Ejecución de E2E Tests en Jenkins**
```bash
# Comando ejecutado en la VM remota
cd "$REMOTE_DIR"
echo "Ejecutando pruebas E2E"
./mvnw -B -pl e2e-tests test -Dtest='*E2E*Test' -DfailIfNoTests=false
summarize_reports "e2e-tests"
```

#### **Configuración de VM**
- **Proveedor**: DigitalOcean
- **Imagen**: Ubuntu 22.04 LTS
- **Tamaño**: s-1vcpu-2gb (configurable)
- **Región**: nyc3 (configurable)
- **Acceso**: SSH con credenciales almacenadas en Jenkins

#### **Parámetros Configurables**
- `VM_NAME`: Nombre de la VM a usar/crear
- `VM_REGION`: Región de DigitalOcean
- `VM_SIZE`: Tamaño de la VM
- `REPO_URL`: URL del repositorio a clonar
- `APP_BRANCH`: Rama específica a usar
- `DEPLOY_TO_K8S`: Desplegar en Kubernetes
- `K8S_ENVIRONMENT`: Ambiente (dev/staging/prod)

#### **Credenciales Requeridas en Jenkins**
- `digitalocean-token`: Token de API de DigitalOcean
- `integration-vm-password`: Contraseña de la VM
- `gcp-service-account`: Credenciales de GCP (para K8s)
- `github-token`: Token de GitHub

#### **Monitoreo y Reportes**
```bash
# Función de resumen de reportes
summarize_reports() {
  local module="$1"
  local report_dir="$module/target/surefire-reports"
  # Extrae estadísticas de XML y TXT
  echo "${module} -> ${summary}"
}
```

#### **Artefactos de Jenkins**
- **Test Reports**: Archivo tar.gz con todos los reportes
- **Docker Images**: Imágenes construidas y subidas a GCR
- **K8s Manifests**: Archivos de despliegue de Kubernetes

---

## Troubleshooting y Problemas Comunes

### **Problemas de Ejecución Local**

#### **1. Error: Puerto en Uso**
```bash
# Síntoma
Port 8080 was already in use

# Solución
# Verificar qué proceso usa el puerto
lsof -i :8080
# O usar otro puerto
export SERVER_PORT=8081
./mvnw test
```

#### **2. Error: Base de Datos H2 No Disponible**
```bash
# Síntoma
java.sql.SQLException: Database "mem:testdb_e2e" not found

# Solución
# Verificar dependencias
./mvnw -pl e2e-tests dependency:tree | grep h2
# Limpiar y reconstruir
./mvnw clean compile
```

#### **3. Error: Timeout en Comunicación HTTP**
```bash
# Síntoma
java.net.SocketTimeoutException: Read timed out

# Solución
# Aumentar timeout
./mvnw test -Dtest.e2e.timeout=60000
# O configurar en application.properties
echo "test.e2e.timeout=60000" >> src/test/resources/application.properties
```

#### **4. Error: Serialización JSON**
```bash
# Síntoma
com.fasterxml.jackson.databind.exc.InvalidDefinitionException

# Solución
# Verificar DTOs tienen constructores por defecto
# Verificar anotaciones Jackson
# Revisar configuración de ObjectMapper
```

### **Problemas de Jenkins**

#### **1. VM No Disponible**
```bash
# Síntoma
No se encontró la VM ecommerce-integration-runner

# Solución
# Verificar credenciales de DigitalOcean
# Revisar parámetro VM_NAME
# Ejecutar Jenkins_Create_VM manualmente
```

#### **2. Error de SSH a VM**
```bash
# Síntoma
Permission denied (publickey,password)

# Solución
# Verificar credencial 'integration-vm-password'
# Verificar que la VM esté corriendo
# Revisar configuración de red
```

#### **3. Tests Fallan en VM**
```bash
# Síntoma
Tests run: 5, Failures: 1, Errors: 0

# Solución
# Revisar logs en la VM
# Verificar que todos los servicios estén disponibles
# Revisar configuración de red entre servicios
```

### **Problemas de GitHub Actions**

#### **1. Workflow No Se Ejecuta**
```bash
# Síntoma
Workflow no aparece en la pestaña Actions

# Solución
# Verificar que el archivo esté en .github/workflows/
# Verificar sintaxis YAML
# Verificar triggers (branches)
```

#### **2. Error de Permisos**
```bash
# Síntoma
Permission denied to create comment

# Solución
# Verificar token de GitHub
# Revisar permisos del repositorio
# Verificar configuración de secrets
```

#### **3. Tests Fallan en Actions**
```bash
# Síntoma
BUILD FAILURE en GitHub Actions

# Solución
# Revisar logs en la pestaña Actions
# Verificar que Java 11 esté configurado
# Revisar dependencias Maven
```

### **Debugging Avanzado**

#### **1. Habilitar Logging Detallado**
```bash
# Configurar logging en application.properties
logging.level.com.selimhorri.app=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

#### **2. Verificar Configuración de Red**
```bash
# Verificar conectividad entre servicios
curl -v http://localhost:8080/api/users
curl -v http://localhost:8700/actuator/health
curl -v http://localhost:8500/actuator/health
```

#### **3. Monitorear Base de Datos H2**
```bash
# Acceder a consola H2 durante tests
# URL: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb_e2e
# Usuario: sa
# Contraseña: (vacía)
```

#### **4. Verificar Dependencias**
```bash
# Ver árbol de dependencias
./mvnw -pl e2e-tests dependency:tree

# Verificar conflictos
./mvnw -pl e2e-tests dependency:analyze

# Resolver dependencias
./mvnw -pl e2e-tests dependency:resolve
```

### **Comandos de Diagnóstico**

#### **Verificar Estado del Proyecto**
```bash
# Verificar estructura
find . -name "*E2E*Test.java" -type f

# Verificar configuración
cat e2e-tests/pom.xml | grep -A 10 -B 5 "artifactId"

# Verificar recursos
ls -la e2e-tests/src/test/resources/
```

#### **Verificar Ejecución**
```bash
# Ejecutar con verbose
./mvnw test -X -Dtest=*E2E*Test

# Ejecutar solo compilación
./mvnw compile -pl e2e-tests

# Verificar tests disponibles
./mvnw test -Dtest=*E2E*Test -DdryRun=true
```

#### **Limpiar y Reconstruir**
```bash
# Limpieza completa
./mvnw clean
rm -rf e2e-tests/target/
./mvnw compile -pl e2e-tests

# Reconstruir desde cero
./mvnw clean install -DskipTests
./mvnw test -pl e2e-tests
```

### **Configuración de Entorno**

#### **Variables de Entorno Recomendadas**
```bash
# Para desarrollo local
export SPRING_PROFILES_ACTIVE=test
export LOGGING_LEVEL_COM_SELIMHORRI_APP=DEBUG
export TEST_E2E_TIMEOUT=30000
export MAVEN_OPTS="-Xmx1024m -XX:MaxPermSize=256m"

# Para Jenkins
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
export MAVEN_HOME=/opt/maven
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
```

#### **Configuración de Maven**
```xml
<!-- En settings.xml -->
<settings>
  <profiles>
    <profile>
      <id>e2e-test</id>
      <properties>
        <maven.test.failure.ignore>false</maven.test.failure.ignore>
        <surefire.reportFormat>html</surefire.reportFormat>
      </properties>
    </profile>
  </profiles>
</settings>
```


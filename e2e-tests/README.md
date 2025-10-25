# Pruebas E2E (End-to-End) - Ecommerce Microservices

## 📋 Descripción

Este módulo contiene las pruebas E2E que validan flujos completos de usuario a través de múltiples microservicios del sistema ecommerce. Las pruebas simulan el comportamiento real de un usuario navegando por la aplicación web completa.

## 🎯 Objetivos

- Validar flujos completos de usuario desde el registro hasta la finalización de compra
- Probar la comunicación entre microservicios en un entorno real
- Verificar la integridad de datos a través de múltiples servicios
- Asegurar que los endpoints REST funcionen correctamente en conjunto

## 🧪 Pruebas Implementadas

### **E2E Test 1: Complete User Registration and Profile Setup**
- **Flujo**: Registro de usuario → Creación de credenciales → Agregar dirección → Verificar perfil completo
- **Servicios involucrados**: user-service, credential-service, address-service
- **Validaciones**: 
  - Creación exitosa de usuario
  - Asociación de credenciales
  - Agregado de dirección
  - Recuperación de perfil completo

### **E2E Test 2: Product Catalog Browsing and Category Management**
- **Flujo**: Crear categoría → Crear productos → Navegar catálogo
- **Servicios involucrados**: product-service, category-service
- **Validaciones**:
  - Creación de categorías
  - Creación de productos con categorías
  - Navegación del catálogo

### **E2E Test 3: Complete Order Creation and Management Flow**
- **Flujo**: Crear orden → Agregar items → Procesar pago → Verificar estado
- **Servicios involucrados**: order-service, shipping-service, payment-service
- **Validaciones**:
  - Creación de orden
  - Agregado de items de orden
  - Procesamiento de pago
  - Verificación de estado de orden

### **E2E Test 4: Favorites Management and User Preferences**
- **Flujo**: Agregar producto a favoritos → Ver favoritos → Eliminar favorito
- **Servicios involucrados**: favourite-service, user-service, product-service
- **Validaciones**:
  - Agregado a favoritos
  - Recuperación de lista de favoritos
  - Eliminación de favoritos

### **E2E Test 5: Complete E-commerce Transaction Flow**
- **Flujo**: Autenticación → Navegación → Compra → Verificación → Limpieza
- **Servicios involucrados**: Todos los microservicios
- **Validaciones**:
  - Flujo completo de compra
  - Verificación de estados
  - Limpieza de datos

## 🛠️ Configuración Técnica

### **Stack Tecnológico**
- **Framework**: Spring Boot Test (`@SpringBootTest`)
- **Base de Datos**: H2 In-Memory Database
- **Cliente HTTP**: TestRestTemplate
- **Serialización**: Jackson ObjectMapper
- **Patrón de Testing**: AAA (Arrange-Act-Assert)

### **Configuración de Base de Datos**
```properties
spring.datasource.url=jdbc:h2:mem:testdb_e2e
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
```

### **Configuración de Servicios**
- Service Discovery deshabilitado para pruebas aisladas
- Eureka Registry deshabilitado
- Logging configurado para debugging

## 🚀 Ejecución

### **Ejecutar todas las pruebas E2E**
```bash
cd e2e-tests
./mvnw clean test -Dtest=*E2E*Test
```

### **Ejecutar prueba específica**
```bash
./mvnw test -Dtest=E2EUserJourneyTest#testCompleteUserRegistrationFlow
```

### **Ejecutar con perfil específico**
```bash
./mvnw test -Dspring.profiles.active=test
```

## 📊 Métricas de Pruebas

- **Total de Pruebas**: 5
- **Servicios Cubiertos**: 6 (user, product, order, payment, favourite, shipping)
- **Tiempo de Ejecución**: ~30-45 segundos
- **Cobertura**: Flujos completos de usuario

## 🔧 Dependencias

### **Microservicios Requeridos**
- user-service
- product-service
- order-service
- payment-service
- favourite-service
- shipping-service

### **Dependencias Maven**
- spring-boot-starter-test
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- h2database
- jackson-databind
- junit-jupiter

## 📝 Patrones de Prueba

### **1. Orden de Ejecución**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Test
@Order(1)
void testUserRegistration() { ... }
```

### **2. Configuración de Cliente HTTP**
```java
@Autowired
private TestRestTemplate restTemplate;

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<Dto> request = new HttpEntity<>(dto, headers);
```

### **3. Validación de Respuestas**
```java
ResponseEntity<Dto> response = restTemplate.exchange(
    baseUrl + "/api/endpoint",
    HttpMethod.POST,
    request,
    Dto.class
);

assertEquals(HttpStatus.OK, response.getStatusCode());
assertNotNull(response.getBody());
```

## 🐛 Troubleshooting

### **Problemas Comunes**

1. **Puerto en uso**: Las pruebas usan `@LocalServerPort` para puerto aleatorio
2. **Timeout de conexión**: Configurar `test.e2e.timeout=30000`
3. **Datos no encontrados**: Verificar orden de ejecución con `@Order`
4. **Errores de serialización**: Verificar DTOs y Jackson configuration

### **Logs de Debug**
```properties
logging.level.com.selimhorri.app=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=DEBUG
```

## 📈 Próximos Pasos

1. **Pruebas de Rendimiento**: Integrar con Locust
2. **Pruebas de Carga**: Simular múltiples usuarios concurrentes
3. **Pruebas de Resilencia**: Validar circuit breakers y fallbacks
4. **Pruebas de Seguridad**: Validar autenticación y autorización

---

**Última actualización**: 2025-10-21  
**Autor**: Oscar Murillo Rodriguez  
**Versión**: 1.0



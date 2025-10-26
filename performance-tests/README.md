# ⚡ Pruebas de Rendimiento y Estrés - E-commerce Microservices

## 📋 Resumen Ejecutivo

Este documento describe la implementación completa de pruebas de rendimiento y estrés para el sistema de microservicios de e-commerce utilizando **Locust**. Las pruebas cubren todos los microservicios principales y simulan casos de uso reales del sistema.

### 🎯 Objetivos

- **Medir el rendimiento** del sistema bajo diferentes cargas de trabajo
- **Identificar cuellos de botella** en microservicios individuales
- **Validar la escalabilidad** del sistema
- **Establecer métricas de referencia** para el rendimiento
- **Detectar problemas de rendimiento** antes del despliegue en producción

---

## 🏗️ Arquitectura de Pruebas

### Microservicios Evaluados

| Microservicio | Puerto | Funcionalidad Principal | Endpoints Evaluados |
|---------------|--------|-------------------------|-------------------|
| **API Gateway** | 8080 | Punto de entrada único | Routing, Load Balancing |
| **User Service** | 8700 | Gestión de usuarios | CRUD usuarios, credenciales |
| **Product Service** | 8500 | Catálogo de productos | CRUD productos, categorías |
| **Order Service** | 8300 | Gestión de pedidos | CRUD pedidos, carritos |
| **Payment Service** | 8400 | Procesamiento de pagos | CRUD pagos, estados |
| **Favourite Service** | 8800 | Productos favoritos | CRUD favoritos |
| **Shipping Service** | 8600 | Gestión de envíos | CRUD items de pedido |

### Tipos de Usuarios Simulados

1. **EcommerceUser** - Usuario típico de e-commerce
2. **HeavyUser** - Usuario intensivo con múltiples operaciones
3. **LightUser** - Usuario con operaciones mínimas
4. **StressTestUser** - Usuario para pruebas de estrés

---

## 📊 Métricas Evaluadas

### Métricas de Rendimiento

- **Tiempo de Respuesta**
  - Promedio
  - Mediana (P50)
  - Percentil 90 (P90)
  - Percentil 95 (P95)
  - Percentil 99 (P99)
  - Máximo

- **Throughput**
  - Requests por segundo
  - Requests por usuario
  - Picos de throughput

- **Tasa de Errores**
  - Porcentaje de requests fallidos
  - Tipos de errores
  - Distribución temporal de errores

### Métricas de Recursos

- **CPU Usage** - Utilización del procesador
- **Memory Usage** - Uso de memoria
- **Network I/O** - Tráfico de red
- **Database Connections** - Conexiones a base de datos

---

## 🧪 Escenarios de Prueba

### 1. Pruebas de Rendimiento Normal

**Objetivo**: Evaluar el rendimiento bajo carga típica de producción

```bash
# Configuración
Usuarios: 20
Spawn Rate: 2 usuarios/segundo
Duración: 10 minutos
Distribución: 75% normales, 15% pesados, 10% ligeros

# Ejecución
./run_tests.sh performance ecommerce 20 2 10m
```

**Métricas Esperadas**:
- P95 Response Time: < 2000ms
- Error Rate: < 1%
- Throughput: > 50 req/s

### 2. Pruebas de Carga Pico

**Objetivo**: Simular períodos de alta demanda (Black Friday)

```bash
# Configuración
Usuarios: 100
Spawn Rate: 10 usuarios/segundo
Duración: 15 minutos
Distribución: 60% normales, 30% pesados, 10% ligeros

# Ejecución
./run_tests.sh performance ecommerce 100 10 15m
```

**Métricas Esperadas**:
- P95 Response Time: < 5000ms
- Error Rate: < 5%
- Throughput: > 200 req/s

### 3. Pruebas de Carga Sostenida

**Objetivo**: Validar estabilidad durante operación continua

```bash
# Configuración
Usuarios: 50
Spawn Rate: 5 usuarios/segundo
Duración: 1 hora
Distribución: 70% normales, 20% pesados, 10% ligeros

# Ejecución
./run_tests.sh performance ecommerce 50 5 1h
```

**Métricas Esperadas**:
- P95 Response Time: < 3000ms
- Error Rate: < 2%
- Throughput: > 100 req/s

### 4. Pruebas de Estrés

**Objetivo**: Encontrar el punto de ruptura del sistema

```bash
# Configuración
Usuarios: 200
Spawn Rate: 20 usuarios/segundo
Duración: 20 minutos
Distribución: 100% usuarios de estrés

# Ejecución
./run_tests.sh stress ecommerce 200 20 20m
```

**Métricas Esperadas**:
- P95 Response Time: < 10000ms
- Error Rate: < 10%
- Throughput: > 500 req/s

### 5. Pruebas de Microservicios Individuales

**Objetivo**: Identificar cuellos de botella específicos

```bash
# User Service
./run_tests.sh performance user-service 30 3 10m

# Product Service
./run_tests.sh performance product-service 40 4 10m

# Order Service
./run_tests.sh performance order-service 25 2 10m

# Payment Service
./run_tests.sh performance payment-service 20 2 10m

# Favourite Service
./run_tests.sh performance favourite-service 35 3 10m

# Shipping Service
./run_tests.sh performance shipping-service 20 2 10m
```

---

## 🚀 Instalación y Configuración

### Prerrequisitos

- Python 3.8+
- pip3
- Docker y Docker Compose
- Microservicios ejecutándose

### Instalación

```bash
# 1. Navegar al directorio de pruebas
cd performance-tests

# 2. Instalar dependencias
./run_tests.sh install

# 3. Verificar que los servicios estén ejecutándose
./run_tests.sh check
```

### Dependencias

```txt
locust==2.17.0
requests==2.31.0
faker==19.6.2
pandas==2.1.1
matplotlib==3.7.2
seaborn==0.12.2
```

---

## 📈 Casos de Uso Simulados

### Flujo de Usuario Típico

1. **Navegación de Productos** (40% del tráfico)
   - Listar productos
   - Ver detalles de producto
   - Buscar por categoría

2. **Gestión de Favoritos** (25% del tráfico)
   - Agregar productos a favoritos
   - Ver lista de favoritos
   - Remover favoritos

3. **Proceso de Compra** (20% del tráfico)
   - Crear pedido
   - Procesar pago
   - Ver historial de pedidos

4. **Gestión de Usuario** (15% del tráfico)
   - Crear cuenta
   - Actualizar perfil
   - Gestionar credenciales

### Patrones de Carga

- **Horario Pico**: 9:00-11:00 y 19:00-21:00
- **Horario Valle**: 2:00-6:00
- **Fin de Semana**: Carga más alta en sábados
- **Eventos Especiales**: Black Friday, Cyber Monday

---

## 📊 Análisis de Resultados

### Interpretación de Métricas

#### Tiempo de Respuesta

| Percentil | Interpretación | Acción Recomendada |
|-----------|----------------|-------------------|
| P50 < 500ms | Excelente | Mantener configuración |
| P95 < 2000ms | Bueno | Monitorear tendencias |
| P95 > 5000ms | Crítico | Optimizar inmediatamente |

#### Throughput

| Valor | Interpretación | Acción Recomendada |
|-------|----------------|-------------------|
| > 200 req/s | Excelente | Escalar horizontalmente |
| 50-200 req/s | Bueno | Monitorear recursos |
| < 50 req/s | Crítico | Investigar cuellos de botella |

#### Tasa de Errores

| Valor | Interpretación | Acción Recomendada |
|-------|----------------|-------------------|
| < 1% | Excelente | Mantener configuración |
| 1-5% | Aceptable | Investigar errores específicos |
| > 5% | Crítico | Resolver problemas inmediatamente |

### Identificación de Cuellos de Botella

1. **Base de Datos**
   - Queries lentas
   - Conexiones agotadas
   - Índices faltantes

2. **Red**
   - Latencia alta
   - Ancho de banda limitado
   - Timeouts de conexión

3. **Aplicación**
   - Código ineficiente
   - Recursos no liberados
   - Algoritmos lentos

4. **Infraestructura**
   - CPU saturada
   - Memoria insuficiente
   - Disco I/O alto

---

## 🔧 Configuración Avanzada

### Variables de Entorno

```bash
# Configuración de Locust
export LOCUST_HOST="http://localhost:8080"
export LOCUST_USERS="50"
export LOCUST_SPAWN_RATE="5"
export LOCUST_RUN_TIME="10m"

# Configuración de reportes
export REPORT_FORMAT="html,csv,json"
export REPORT_INCLUDE_CHARTS="true"
```

### Personalización de Pruebas

```python
# Ejemplo: Personalizar comportamiento de usuario
class CustomEcommerceUser(EcommerceUser):
    wait_time = between(0.5, 2)  # Tiempo de espera personalizado
    
    @task(15)
    def custom_product_search(self):
        # Implementar búsqueda personalizada
        pass
```

---

## 📋 Checklist de Pruebas

### Antes de Ejecutar Pruebas

- [ ] Todos los microservicios están ejecutándose
- [ ] Base de datos está poblada con datos de prueba
- [ ] Red está estable y sin limitaciones
- [ ] Recursos del sistema están disponibles
- [ ] Monitoreo está configurado

### Durante las Pruebas

- [ ] Monitorear métricas en tiempo real
- [ ] Verificar logs de errores
- [ ] Observar uso de recursos
- [ ] Documentar comportamientos anómalos

### Después de las Pruebas

- [ ] Generar reportes completos
- [ ] Analizar resultados
- [ ] Identificar cuellos de botella
- [ ] Documentar recomendaciones
- [ ] Planificar optimizaciones

---

## 🚨 Troubleshooting

### Problemas Comunes

#### 1. Servicios No Responden

```bash
# Verificar estado de servicios
./run_tests.sh check

# Reiniciar servicios si es necesario
cd ../scripts
./stop-services.sh
./start-services.sh
```

#### 2. Errores de Conexión

```bash
# Verificar conectividad
curl -f http://localhost:8080/app/actuator/health

# Verificar puertos
netstat -tlnp | grep :8080
```

#### 3. Rendimiento Degradado

```bash
# Verificar recursos del sistema
top
htop
iostat
```

#### 4. Errores de Memoria

```bash
# Verificar memoria disponible
free -h
df -h
```

### Logs y Debugging

```bash
# Ver logs de Locust
tail -f locust.log

# Ver logs de microservicios
docker logs <container_name>

# Ver logs del sistema
journalctl -f
```

---

## 📈 Mejores Prácticas

### 1. Planificación de Pruebas

- **Definir objetivos** claros antes de ejecutar
- **Establecer métricas de referencia** basadas en requisitos
- **Planificar diferentes escenarios** de carga
- **Documentar expectativas** y tolerancias

### 2. Ejecución de Pruebas

- **Ejecutar pruebas incrementales** (carga baja a alta)
- **Monitorear recursos** durante las pruebas
- **Documentar condiciones** del entorno
- **Ejecutar múltiples iteraciones** para validar consistencia

### 3. Análisis de Resultados

- **Comparar con métricas de referencia**
- **Identificar patrones** en los datos
- **Correlacionar métricas** de diferentes niveles
- **Documentar hallazgos** y recomendaciones

### 4. Optimización Continua

- **Implementar mejoras** basadas en resultados
- **Re-ejecutar pruebas** después de optimizaciones
- **Establecer monitoreo continuo** en producción
- **Actualizar métricas de referencia** regularmente

---

## 🔄 Integración con CI/CD

### Pipeline de Pruebas

```yaml
# Ejemplo de pipeline Jenkins/GitHub Actions
stages:
  - name: "Performance Tests"
    steps:
      - name: "Install Dependencies"
        run: "./run_tests.sh install"
      
      - name: "Check Services"
        run: "./run_tests.sh check"
      
      - name: "Run Quick Tests"
        run: "./run_tests.sh performance ecommerce 20 2 5m"
      
      - name: "Generate Report"
        run: "./generate_report.py --test-name ecommerce --timestamp $TIMESTAMP"
      
      - name: "Archive Results"
        run: "tar -czf performance_results.tar.gz results/"
```

### Criterios de Aceptación

```bash
# Script de validación automática
#!/bin/bash
ERROR_RATE=$(grep "Error rate" results/ecommerce_*.csv | awk '{print $2}')
RESPONSE_TIME=$(grep "95%" results/ecommerce_*.csv | awk '{print $2}')

if [ $(echo "$ERROR_RATE > 0.05" | bc) -eq 1 ]; then
    echo "ERROR: Error rate too high: $ERROR_RATE"
    exit 1
fi

if [ $(echo "$RESPONSE_TIME > 5000" | bc) -eq 1 ]; then
    echo "ERROR: Response time too high: $RESPONSE_TIME"
    exit 1
fi

echo "Performance tests passed!"
```

---

## 📚 Referencias y Recursos

### Documentación Oficial

- [Locust Documentation](https://docs.locust.io/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Docker Compose](https://docs.docker.com/compose/)

### Herramientas de Monitoreo

- **APM**: New Relic, Datadog, AppDynamics
- **Logs**: ELK Stack, Splunk, Fluentd
- **Métricas**: Prometheus, Grafana, InfluxDB
- **Tracing**: Jaeger, Zipkin, OpenTelemetry

### Recursos Adicionales

- [Performance Testing Best Practices](https://martinfowler.com/articles/nonDeterminism.html)
- [Microservices Performance Patterns](https://microservices.io/patterns/microservices.html)
- [Load Testing Strategies](https://www.thoughtworks.com/radar/techniques/load-testing)

---

## 📞 Soporte y Contacto

Para preguntas, problemas o sugerencias relacionadas con las pruebas de rendimiento:

- **Documentación**: Este archivo README
- **Issues**: Crear un issue en el repositorio
- **Discusiones**: Usar las discusiones del repositorio

---

**Última actualización**: $(date)
**Versión**: 1.0.0
**Autor**: Equipo de Desarrollo

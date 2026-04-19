# Caching Lab — Post-Contenido 2, Unidad 8

**Diseño de Algoritmos y Sistemas — Ingeniería de Sistemas**  
**Universidad de Santander (UDES) — 2026**  
**Autor:** Toscano

---

## Descripción

Sistema de caching completo implementado en Java 17 que incluye:

1. **LRU Cache thread-safe** con `HashMap` y lista doblemente enlazada
2. **Write-Through Cache** con escritura síncrona a repositorio
3. **Write-Behind Cache** (write-back) con escritura asíncrona
4. **Optimistic Store** con control de concurrencia basado en versiones (ETag)
5. **Benchmarks** que comparan rendimiento con y sin cache

---

## Estructura del Proyecto

```
toscano-post2-u8/
├── pom.xml                                  # Configuración Maven
├── README.md
└── src/
    ├── main/java/
    │   ├── cache/
    │   │   ├── Node.java                    # Nodo de lista doblemente enlazada
    │   │   ├── LRUCache.java                # LRU Cache thread-safe
    │   │   ├── CacheRepository.java         # Interfaz del repositorio
    │   │   ├── InMemoryRepository.java      # Implementación en memoria
    │   │   ├── WriteThroughCache.java        # Write-through con repositorio
    │   │   ├── WriteBehindCache.java         # Write-behind asíncrono
    │   │   ├── OptimisticStore.java          # Control optimista con versiones
    │   │   ├── OptimisticLockException.java  # Excepción de conflicto de versión
    │   │   └── TestRunner.java              # Test runner independiente
    │   └── benchmark/
    │       ├── CacheBenchmark.java           # Benchmark JMH (requiere Maven)
    │       └── SimpleBenchmark.java          # Benchmark simplificado standalone
    └── test/java/cache/
        ├── LRUCacheTest.java                 # Tests JUnit 5 para LRU Cache
        ├── WriteThroughCacheTest.java         # Tests JUnit 5 para Write-Through
        ├── WriteBehindCacheTest.java          # Tests JUnit 5 para Write-Behind
        └── OptimisticStoreTest.java           # Tests JUnit 5 para Optimistic Store
```

---

## Compilación y Ejecución

### Con Maven (recomendado)

```bash
# Compilar
mvn clean compile

# Ejecutar tests JUnit
mvn test

# Ejecutar benchmarks JMH
mvn clean package
java -jar target/benchmarks.jar CacheBenchmark

# Ejecutar benchmarks JMH con parámetros específicos
java -jar target/benchmarks.jar CacheBenchmark -p cacheSize=100,1000,10000
```

### Sin Maven (compilación directa)

```bash
# Compilar
mkdir -p build/classes
javac --release 17 -d build/classes $(find src/main/java/cache -name "*.java") \
  src/main/java/benchmark/SimpleBenchmark.java

# Ejecutar tests
java -cp build/classes cache.TestRunner

# Ejecutar benchmarks
java -cp build/classes benchmark.SimpleBenchmark
```

---

## Componentes Implementados

### 1. LRU Cache (`LRUCache.java`)

Cache con política de reemplazo *Least Recently Used* implementado con `HashMap` + lista doblemente enlazada.

- **Complejidad:** `get()` y `put()` son O(1) amortizado.
- **Thread-safety:** `ReentrantReadWriteLock` protege todas las operaciones.
- **Invariante:** `map.size() == tamaño_lista <= capacity`.

La lista doblemente enlazada mantiene el orden de uso: el nodo más recientemente accedido está al frente (después del centinela `head`) y el menos usado al final (antes del centinela `tail`). Al evictar, se remueve `tail.prev`.

### 2. Write-Through Cache (`WriteThroughCache.java`)

Estrategia de escritura síncrona: cada `put()` escribe primero al repositorio (fuente de verdad) y luego al cache.

- **Garantía:** si `put()` retorna sin excepción, el dato está en ambos.
- **Cache miss:** `get()` consulta el repositorio y puebla el cache automáticamente.
- **Trade-off:** mayor latencia de escritura a cambio de consistencia fuerte.

### 3. Write-Behind Cache (`WriteBehindCache.java`)

Estrategia de escritura asíncrona: `put()` escribe inmediatamente al cache y encola la persistencia.

- Un hilo dedicado (`SingleThreadExecutor`) consume la `LinkedBlockingQueue` y persiste los datos.
- **Riesgo:** si el proceso termina antes del flush, los datos encolados se pierden.
- **Mitigación:** método `flush()` y uso de shutdown hooks.
- **Trade-off:** menor latencia de escritura a cambio de riesgo de pérdida de datos.

### 4. Optimistic Store (`OptimisticStore.java`)

Almacén con control de concurrencia optimista basado en versiones, simulando ETags HTTP.

- `put()` crea entradas con versión 1.
- `updateIfMatch()` actualiza solo si la versión actual coincide con la esperada (análogo a `If-Match`).
- Usa `ConcurrentHashMap.compute()` para atomicidad.
- Lanza `OptimisticLockException` en conflictos (análogo a HTTP 412).

---

## Tabla Comparativa: Write-Through vs Write-Behind

| Aspecto | Write-Through | Write-Behind |
|---|---|---|
| **Latencia de escritura** | Alta (síncrona al repo) | Baja (solo cache + enqueue) |
| **Consistencia** | Fuerte: cache y repo siempre sincronizados | Eventual: el repo puede estar desfasado |
| **Riesgo de pérdida** | Ninguno (si repo es durable) | Si el proceso falla antes del flush |
| **Throughput de escritura** | Limitado por latencia del repo | Alto (escritura desacoplada) |
| **Complejidad** | Baja | Media (hilo de escritura, cola, shutdown) |
| **Caso de uso ideal** | Datos críticos, baja frecuencia de escritura | Alto volumen de escrituras, datos tolerantes a pérdida |

### ¿Cuándo usar cada estrategia?

- **Write-Through:** transacciones financieras, datos de usuario, configuración crítica — cualquier escenario donde la pérdida de datos es inaceptable.
- **Write-Behind:** logs, métricas, contadores de vistas, datos analíticos — escenarios donde el rendimiento importa más que la durabilidad inmediata.

---

## Resultados de Benchmark

### Configuración

- **JDK:** OpenJDK 21
- **Warmup:** 3 iteraciones × 1 segundo
- **Medición:** 5 iteraciones × 1 segundo
- **Dataset:** 10× el tamaño del cache (para forzar misses)
- **Patrón de acceso:** aleatorio uniforme

### Resultados

| Benchmark                         | cacheSize | Mode  | Cnt | Score     | Error      | Units  |
|----------------------------------|----------:|-------|----:|----------:|-----------:|--------|
| CacheBenchmark.withCache         |       100 | thrpt |   5 | 11678,223 | ± 1007,147 | ops/ms |
| CacheBenchmark.withCache         |      1000 | thrpt |   5 | 11768,756 | ± 1289,896 | ops/ms |
| CacheBenchmark.withCache         |     10000 | thrpt |   5 |  2512,421 | ± 2163,725 | ops/ms |
| CacheBenchmark.withoutCache      |       100 | thrpt |   5 | 44826,533 | ± 21627,313| ops/ms |
| CacheBenchmark.withoutCache      |      1000 | thrpt |   5 | 32783,810 | ± 5454,048 | ops/ms |
| CacheBenchmark.withoutCache      |     10000 | thrpt |   5 |  7691,695 | ± 2576,502 | ops/ms |

### Análisis

En este benchmark sintético, el acceso directo al `HashMap` (`withoutCache`) es más rápido que el acceso vía `LRUCache` (`withCache`). Esto se explica porque:

1. El `LRUCache` añade overhead por: adquisición del `ReentrantReadWriteLock`, manipulación de la lista doblemente enlazada en cada acceso, y lógica de evicción.
2. El "repositorio" simulado es un `HashMap` en memoria, que ya es O(1). En producción, el repositorio sería una base de datos o servicio remoto con latencia de milisegundos, donde el cache eliminaría esa latencia en los hits.
3. Con patrón de acceso uniforme y dataset 10× la capacidad del cache, la tasa de hit es ~10%, lo cual minimiza el beneficio del cache. Con distribución Zipf (pocos elementos accedidos frecuentemente), la tasa de hit sería mucho mayor.

**Conclusión:** El cache brinda su mayor valor cuando el repositorio subyacente tiene alta latencia (disco, red) y el patrón de acceso tiene alta localidad temporal.

---

## Diagrama de Flujo: Operación `put()` en cada modo

### Write-Through `put(key, value)`

```
Cliente llama put(key, value)
        │
        ▼
  repo.write(key, value)   ← Escritura síncrona al repositorio
        │
        ├── ¿Excepción? ──▶ Propagar al cliente (dato NO en cache)
        │
        ▼
  cache.put(key, value)    ← Actualizar/insertar en LRU Cache
        │
        ├── ¿Cache lleno? ──▶ evict() LRU node
        │
        ▼
  Retornar al cliente       ← Dato en AMBOS: cache + repo
```

### Write-Behind `put(key, value)`

```
Cliente llama put(key, value)
        │
        ▼
  cache.put(key, value)       ← Escritura inmediata al cache
        │
        ├── ¿Cache lleno? ──▶ evict() LRU node
        │
        ▼
  writeQueue.offer(entry)     ← Encolar para escritura asíncrona
        │
        ▼
  Retornar al cliente          ← Dato en cache, PENDIENTE en repo
        
        ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ (hilo de escritura, asíncrono) ┄┄┄┄┄┄
        
  writeQueue.take()            ← Hilo dedicado consume la cola
        │
        ▼
  repo.write(key, value)       ← Persistencia eventual
```

---

## Tests y Checkpoints

Se cubren los 4 checkpoints del laboratorio:

| # | Checkpoint | Estado |
|---|---|---|
| 1 | `LRUCache(3)`: put(A), put(B), put(C), get(A), put(D) → evicta B, `size() == 3` | ✓ |
| 2 | `WriteThroughCacheTest`: after `put(k,v)`, `repo.read(k)` retorna `v`. Tras evicción, `get(k)` recupera desde repo | ✓ |
| 3 | `WriteBehindCache`: escritura inmediata a cache, persistencia asíncrona tras `flush()` | ✓ |
| 4 | `OptimisticStoreTest`: dos hilos leen la misma versión, uno escribe exitosamente, el otro recibe `OptimisticLockException` | ✓ |

### Tests de concurrencia con múltiples hilos reales

- **LRU Cache:** 8 hilos × 1,000 ops, verificación de invariante de tamaño
- **WriteThroughCache:** 8 hilos × 200 ops, verificación de consistencia cache-repo
- **OptimisticStore:** 10 hilos × 50 incrementos con retry pattern, verificación de contador final

**Total de tests:** 29 (todos pasan)

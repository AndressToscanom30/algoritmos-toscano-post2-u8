package cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para {@link LRUCache}.
 * Cubre: operaciones básicas, evicción LRU, actualización de valores,
 * capacidad 1, y concurrencia con múltiples hilos reales.
 */
class LRUCacheTest {

    private LRUCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new LRUCache<>(3);
    }

    @Test
    @DisplayName("put y get básicos retornan el valor correcto")
    void testPutAndGet() {
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        assertEquals(Optional.of("1"), cache.get("A"));
        assertEquals(Optional.of("2"), cache.get("B"));
        assertEquals(Optional.of("3"), cache.get("C"));
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("get de clave inexistente retorna Optional.empty()")
    void testGetMiss() {
        assertEquals(Optional.empty(), cache.get("X"));
    }

    @Test
    @DisplayName("Checkpoint: put(A), put(B), put(C), get(A), put(D) → evicta B")
    void testLRUEvictionCheckpoint() {
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        // Acceder a A lo mueve al frente, orden LRU: B, C, A
        cache.get("A");

        // Insertar D debe evictar B (el menos recientemente usado)
        cache.put("D", "4");

        assertEquals(3, cache.size());
        assertEquals(Optional.of("1"), cache.get("A"), "A debe seguir en cache");
        assertEquals(Optional.empty(), cache.get("B"), "B debe haber sido evictado");
        assertEquals(Optional.of("3"), cache.get("C"), "C debe seguir en cache");
        assertEquals(Optional.of("4"), cache.get("D"), "D debe estar en cache");
    }

    @Test
    @DisplayName("Actualizar valor existente no cambia el tamaño")
    void testUpdateExistingKey() {
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("A", "updated");

        assertEquals(2, cache.size());
        assertEquals(Optional.of("updated"), cache.get("A"));
    }

    @Test
    @DisplayName("Actualizar clave existente la mueve al frente (evita evicción)")
    void testUpdateMovesToFront() {
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        // Actualizar A la mueve al frente, orden LRU: B, C, A
        cache.put("A", "updated");

        // Insertar D debe evictar B
        cache.put("D", "4");

        assertEquals(Optional.of("updated"), cache.get("A"));
        assertEquals(Optional.empty(), cache.get("B"));
    }

    @Test
    @DisplayName("Cache con capacidad 1 siempre tiene solo un elemento")
    void testCapacityOne() {
        var smallCache = new LRUCache<String, String>(1);
        smallCache.put("A", "1");
        assertEquals(1, smallCache.size());

        smallCache.put("B", "2");
        assertEquals(1, smallCache.size());
        assertEquals(Optional.empty(), smallCache.get("A"));
        assertEquals(Optional.of("2"), smallCache.get("B"));
    }

    @Test
    @DisplayName("Capacidad <= 0 lanza IllegalArgumentException")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(-1));
    }

    @Test
    @DisplayName("Cache lleno evicta correctamente en secuencia")
    void testSequentialEviction() {
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        // Cache lleno: A, B, C (LRU order: A es el más antiguo)

        cache.put("D", "4"); // evicta A
        assertEquals(Optional.empty(), cache.get("A"));

        cache.put("E", "5"); // evicta B (ya que D y C fueron accedidos via get/put)
        assertEquals(Optional.empty(), cache.get("B"));

        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("Concurrencia: múltiples hilos hacen put/get sin errores")
    void testConcurrentAccess() throws InterruptedException {
        var concurrentCache = new LRUCache<Integer, Integer>(100);
        int numThreads = 8;
        int opsPerThread = 1000;
        var latch = new CountDownLatch(numThreads);
        var errors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = threadId * 1000 + i;
                        concurrentCache.put(key, key * 2);
                        Optional<Integer> result = concurrentCache.get(key);
                        // El valor puede haber sido evictado por otro hilo
                        result.ifPresent(v -> {
                            if (v != key * 2) errors.incrementAndGet();
                        });
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, errors.get(), "No debe haber errores de concurrencia");
        assertTrue(concurrentCache.size() <= 100,
                "El tamaño nunca debe exceder la capacidad");
    }

    @Test
    @DisplayName("Concurrencia: el tamaño nunca excede la capacidad bajo carga")
    void testConcurrentSizeInvariant() throws InterruptedException {
        var concurrentCache = new LRUCache<Integer, Integer>(50);
        int numThreads = 10;
        int opsPerThread = 500;
        var latch = new CountDownLatch(numThreads);
        List<Integer> observedSizes = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        concurrentCache.put(i, i);
                        observedSizes.add(concurrentCache.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        for (int size : observedSizes) {
            assertTrue(size <= 50,
                    "Tamaño observado " + size + " excede la capacidad 50");
        }
    }
}

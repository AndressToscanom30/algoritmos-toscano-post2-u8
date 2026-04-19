package cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para {@link OptimisticStore}.
 * Cubre: CRUD básico, control optimista con versiones,
 * conflicto de versiones, y concurrencia con múltiples hilos reales.
 */
class OptimisticStoreTest {

    private OptimisticStore<String, String> store;

    @BeforeEach
    void setUp() {
        store = new OptimisticStore<>();
    }

    @Test
    @DisplayName("put crea entrada con versión 1")
    void testPutCreatesVersionOne() {
        var result = store.put("key", "value");

        assertEquals("value", result.value());
        assertEquals(1L, result.version());
    }

    @Test
    @DisplayName("get retorna valor con versión correcta")
    void testGetReturnsVersioned() {
        store.put("key", "value");

        var result = store.get("key");
        assertTrue(result.isPresent());
        assertEquals("value", result.get().value());
        assertEquals(1L, result.get().version());
    }

    @Test
    @DisplayName("get de clave inexistente retorna empty")
    void testGetMiss() {
        assertEquals(Optional.empty(), store.get("noexiste"));
    }

    @Test
    @DisplayName("updateIfMatch exitoso incrementa versión")
    void testUpdateIfMatchSuccess() {
        store.put("key", "v1");

        var updated = store.updateIfMatch("key", "v2", 1L);

        assertEquals("v2", updated.value());
        assertEquals(2L, updated.version());
    }

    @Test
    @DisplayName("updateIfMatch con versión incorrecta lanza OptimisticLockException")
    void testUpdateIfMatchVersionMismatch() {
        store.put("key", "v1");
        // Actualizar a v2 (versión pasa a 2)
        store.updateIfMatch("key", "v2", 1L);

        // Intentar actualizar con versión antigua (1) debe fallar
        assertThrows(OptimisticLockException.class,
                () -> store.updateIfMatch("key", "v3", 1L),
                "Debe lanzar OptimisticLockException por versión obsoleta");
    }

    @Test
    @DisplayName("updateIfMatch en clave inexistente lanza IllegalStateException")
    void testUpdateIfMatchKeyNotFound() {
        assertThrows(IllegalStateException.class,
                () -> store.updateIfMatch("noexiste", "v1", 1L));
    }

    @Test
    @DisplayName("Actualizaciones sucesivas incrementan la versión correctamente")
    void testSequentialUpdates() {
        store.put("key", "v1");
        store.updateIfMatch("key", "v2", 1L);
        store.updateIfMatch("key", "v3", 2L);
        var result = store.updateIfMatch("key", "v4", 3L);

        assertEquals("v4", result.value());
        assertEquals(4L, result.version());
    }

    @Test
    @DisplayName("Checkpoint: dos hilos leen la misma versión, el primero escribe, el segundo falla")
    void testConcurrentConflict() throws InterruptedException {
        store.put("shared", "initial");

        var barrier = new CyclicBarrier(2);
        var successCount = new AtomicInteger(0);
        var failCount = new AtomicInteger(0);

        // Ambos hilos leen versión 1
        Runnable task = () -> {
            try {
                // Leer la versión actual
                var versioned = store.get("shared").orElseThrow();
                long readVersion = versioned.version();

                // Sincronizar: ambos hilos leen antes de escribir
                barrier.await(5, TimeUnit.SECONDS);

                // Intentar actualizar con la versión leída
                store.updateIfMatch("shared", "updated-by-" +
                        Thread.currentThread().getName(), readVersion);
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                failCount.incrementAndGet();
            } catch (Exception e) {
                // Otros errores inesperados
                fail("Error inesperado: " + e.getMessage());
            }
        };

        var t1 = new Thread(task, "thread-1");
        var t2 = new Thread(task, "thread-2");

        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        assertEquals(1, successCount.get(),
                "Exactamente un hilo debe escribir exitosamente");
        assertEquals(1, failCount.get(),
                "Exactamente un hilo debe recibir OptimisticLockException");

        // La versión final debe ser 2
        var final_ = store.get("shared").orElseThrow();
        assertEquals(2L, final_.version(),
                "La versión final debe ser 2 (una actualización exitosa)");
    }

    @Test
    @DisplayName("Concurrencia: múltiples hilos con retry pattern")
    void testConcurrentWithRetry() throws InterruptedException {
        store.put("counter", "0");

        int numThreads = 10;
        int incrementsPerThread = 50;
        var latch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        boolean success = false;
                        while (!success) {
                            var current = store.get("counter").orElseThrow();
                            int currentVal = Integer.parseInt(current.value());
                            try {
                                store.updateIfMatch("counter",
                                        String.valueOf(currentVal + 1),
                                        current.version());
                                success = true;
                            } catch (OptimisticLockException e) {
                                // Releer y reintentar (patrón optimista)
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        var finalVal = store.get("counter").orElseThrow();
        assertEquals(String.valueOf(numThreads * incrementsPerThread),
                finalVal.value(),
                "El contador debe ser " + (numThreads * incrementsPerThread));
        assertEquals(numThreads * incrementsPerThread + 1, finalVal.version(),
                "La versión debe ser incrementos + 1 (por el put inicial)");
    }
}

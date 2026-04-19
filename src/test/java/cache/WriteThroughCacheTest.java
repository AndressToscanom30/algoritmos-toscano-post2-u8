package cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para {@link WriteThroughCache}.
 * Cubre: write-through, cache miss con lectura de repo,
 * evicción y recuperación desde repo, y concurrencia.
 */
class WriteThroughCacheTest {

    private WriteThroughCache<String, String> wtCache;
    private InMemoryRepository<String, String> repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepository<>();
        wtCache = new WriteThroughCache<>(3, repo);
    }

    @Test
    @DisplayName("Checkpoint: after put(k,v), repo.read(k) retorna v")
    void testWriteThroughPersistsToRepo() {
        wtCache.put("key1", "value1");

        // Verificar que está en el repositorio
        assertEquals(Optional.of("value1"), repo.read("key1"),
                "El valor debe estar en el repositorio tras put()");

        // Verificar que está en el cache
        assertEquals(Optional.of("value1"), wtCache.get("key1"),
                "El valor debe estar en el cache tras put()");
    }

    @Test
    @DisplayName("Checkpoint: después de evicción, get(k) hace cache miss y recupera desde repo")
    void testCacheMissRecoverFromRepo() {
        // Llenar el cache (capacidad 3)
        wtCache.put("A", "1");
        wtCache.put("B", "2");
        wtCache.put("C", "3");

        // Insertar D, evicta A del cache (pero A sigue en repo)
        wtCache.put("D", "4");

        // A fue evictado del cache pero debe recuperarse desde repo
        assertEquals(Optional.of("1"), wtCache.get("A"),
                "A debe recuperarse desde el repositorio tras evicción del cache");
    }

    @Test
    @DisplayName("Múltiples put sobreescriben en cache y repo")
    void testOverwrite() {
        wtCache.put("key", "v1");
        wtCache.put("key", "v2");

        assertEquals(Optional.of("v2"), wtCache.get("key"));
        assertEquals(Optional.of("v2"), repo.read("key"));
    }

    @Test
    @DisplayName("get de clave inexistente retorna empty")
    void testGetMiss() {
        assertEquals(Optional.empty(), wtCache.get("noexiste"));
    }

    @Test
    @DisplayName("Dato pre-existente en repo se carga al cache en cache miss")
    void testCachePopulationOnMiss() {
        // Escribir directamente al repo (sin pasar por cache)
        repo.write("directKey", "directValue");

        // El cache no lo tiene, pero el repo sí
        assertEquals(Optional.of("directValue"), wtCache.get("directKey"),
                "Debe cargar desde repo en cache miss");
    }

    @Test
    @DisplayName("Concurrencia: múltiples hilos escriben y leen sin corrupción")
    void testConcurrentWriteThrough() throws InterruptedException {
        var concurrentRepo = new InMemoryRepository<Integer, Integer>();
        var concurrentCache = new WriteThroughCache<>(50, concurrentRepo);
        int numThreads = 8;
        int opsPerThread = 200;
        var latch = new CountDownLatch(numThreads);
        var errors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = threadId * 1000 + i;
                        concurrentCache.put(key, key * 10);

                        // Verificar consistencia: repo debe tener el valor
                        Optional<Integer> fromRepo = concurrentRepo.read(key);
                        if (fromRepo.isEmpty() || fromRepo.get() != key * 10) {
                            errors.incrementAndGet();
                        }
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

        assertEquals(0, errors.get(),
                "Write-through debe garantizar consistencia cache-repo");
    }
}

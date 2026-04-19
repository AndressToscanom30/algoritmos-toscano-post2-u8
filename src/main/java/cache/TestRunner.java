package cache;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test runner independiente para verificar la lógica de todos los componentes.
 * No requiere JUnit — se puede ejecutar directamente con java.
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=" .repeat(60));
        System.out.println("  CACHING LAB - TEST SUITE");
        System.out.println("=" .repeat(60));

        // === LRUCache Tests ===
        section("LRU Cache Tests");
        testLRUBasicPutGet();
        testLRUGetMiss();
        testLRUEvictionCheckpoint();
        testLRUUpdateExisting();
        testLRUUpdateMovesToFront();
        testLRUCapacityOne();
        testLRUInvalidCapacity();
        testLRUConcurrentAccess();
        testLRUConcurrentSizeInvariant();

        // === WriteThroughCache Tests ===
        section("WriteThroughCache Tests");
        testWTWritePersistsToRepo();
        testWTCacheMissRecoverFromRepo();
        testWTOverwrite();
        testWTGetMiss();
        testWTCachePopulationOnMiss();
        testWTConcurrent();

        // === WriteBehindCache Tests ===
        section("WriteBehindCache Tests");
        testWBPutWritesToCacheImmediately();
        testWBPutPersistsAfterFlush();
        testWBMultipleWrites();

        // === OptimisticStore Tests ===
        section("OptimisticStore Tests");
        testOSPutCreatesVersionOne();
        testOSGetReturnsVersioned();
        testOSGetMiss();
        testOSUpdateIfMatchSuccess();
        testOSUpdateIfMatchVersionMismatch();
        testOSUpdateIfMatchKeyNotFound();
        testOSSequentialUpdates();
        testOSConcurrentConflict();
        testOSConcurrentWithRetry();

        // === Summary ===
        System.out.println("\n" + "=" .repeat(60));
        System.out.printf("  RESULTS: %d passed, %d failed, %d total%n",
                passed, failed, passed + failed);
        System.out.println("=" .repeat(60));

        if (failed > 0) System.exit(1);
    }

    // ===================== LRU CACHE TESTS =====================

    static void testLRUBasicPutGet() {
        var cache = new LRUCache<String, String>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        check("LRU: put/get básicos",
                cache.get("A").equals(Optional.of("1")) &&
                cache.get("B").equals(Optional.of("2")) &&
                cache.get("C").equals(Optional.of("3")) &&
                cache.size() == 3);
    }

    static void testLRUGetMiss() {
        var cache = new LRUCache<String, String>(3);
        check("LRU: get miss retorna empty",
                cache.get("X").equals(Optional.empty()));
    }

    static void testLRUEvictionCheckpoint() {
        var cache = new LRUCache<String, String>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        cache.get("A"); // mueve A al frente
        cache.put("D", "4"); // evicta B

        check("LRU Checkpoint: put(A),put(B),put(C),get(A),put(D) → evicta B, size==3",
                cache.size() == 3 &&
                cache.get("A").equals(Optional.of("1")) &&
                cache.get("B").equals(Optional.empty()) &&
                cache.get("C").equals(Optional.of("3")) &&
                cache.get("D").equals(Optional.of("4")));
    }

    static void testLRUUpdateExisting() {
        var cache = new LRUCache<String, String>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("A", "updated");
        check("LRU: actualizar clave existente no cambia tamaño",
                cache.size() == 2 &&
                cache.get("A").equals(Optional.of("updated")));
    }

    static void testLRUUpdateMovesToFront() {
        var cache = new LRUCache<String, String>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        cache.put("A", "updated"); // mueve A al frente
        cache.put("D", "4");       // evicta B
        check("LRU: update mueve al frente, evicta B",
                cache.get("A").equals(Optional.of("updated")) &&
                cache.get("B").equals(Optional.empty()));
    }

    static void testLRUCapacityOne() {
        var cache = new LRUCache<String, String>(1);
        cache.put("A", "1");
        cache.put("B", "2");
        check("LRU: capacidad 1",
                cache.size() == 1 &&
                cache.get("A").equals(Optional.empty()) &&
                cache.get("B").equals(Optional.of("2")));
    }

    static void testLRUInvalidCapacity() {
        boolean threw0 = false, threwNeg = false;
        try { new LRUCache<>(0); } catch (IllegalArgumentException e) { threw0 = true; }
        try { new LRUCache<>(-1); } catch (IllegalArgumentException e) { threwNeg = true; }
        check("LRU: capacidad <= 0 lanza IllegalArgumentException",
                threw0 && threwNeg);
    }

    static void testLRUConcurrentAccess() throws Exception {
        var cache = new LRUCache<Integer, Integer>(100);
        int numThreads = 8, opsPerThread = 1000;
        var latch = new CountDownLatch(numThreads);
        var errors = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int tid = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = tid * 1000 + i;
                        cache.put(key, key * 2);
                        cache.get(key).ifPresent(v -> {
                            if (v != key * 2) errors.incrementAndGet();
                        });
                    }
                } catch (Exception e) { errors.incrementAndGet(); }
                finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        check("LRU: concurrencia sin errores, tamaño <= capacidad",
                errors.get() == 0 && cache.size() <= 100);
    }

    static void testLRUConcurrentSizeInvariant() throws Exception {
        var cache = new LRUCache<Integer, Integer>(50);
        int numThreads = 10, opsPerThread = 500;
        var latch = new CountDownLatch(numThreads);
        List<Integer> sizes = Collections.synchronizedList(new ArrayList<>());
        var executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        cache.put(i, i);
                        sizes.add(cache.size());
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        boolean allValid = sizes.stream().allMatch(s -> s <= 50);
        check("LRU: invariante de tamaño bajo carga concurrente", allValid);
    }

    // ===================== WRITE-THROUGH TESTS =====================

    static void testWTWritePersistsToRepo() {
        var repo = new InMemoryRepository<String, String>();
        var wt = new WriteThroughCache<>(3, repo);
        wt.put("key1", "value1");
        check("WT Checkpoint: put(k,v) → repo.read(k) retorna v",
                repo.read("key1").equals(Optional.of("value1")) &&
                wt.get("key1").equals(Optional.of("value1")));
    }

    static void testWTCacheMissRecoverFromRepo() {
        var repo = new InMemoryRepository<String, String>();
        var wt = new WriteThroughCache<>(3, repo);
        wt.put("A", "1");
        wt.put("B", "2");
        wt.put("C", "3");
        wt.put("D", "4"); // evicta A del cache
        check("WT Checkpoint: tras evicción, get(A) recupera desde repo",
                wt.get("A").equals(Optional.of("1")));
    }

    static void testWTOverwrite() {
        var repo = new InMemoryRepository<String, String>();
        var wt = new WriteThroughCache<>(3, repo);
        wt.put("key", "v1");
        wt.put("key", "v2");
        check("WT: overwrite actualiza cache y repo",
                wt.get("key").equals(Optional.of("v2")) &&
                repo.read("key").equals(Optional.of("v2")));
    }

    static void testWTGetMiss() {
        var repo = new InMemoryRepository<String, String>();
        var wt = new WriteThroughCache<>(3, repo);
        check("WT: get miss retorna empty",
                wt.get("noexiste").equals(Optional.empty()));
    }

    static void testWTCachePopulationOnMiss() {
        var repo = new InMemoryRepository<String, String>();
        var wt = new WriteThroughCache<>(3, repo);
        repo.write("directKey", "directValue");
        check("WT: cache miss lee desde repo y puebla cache",
                wt.get("directKey").equals(Optional.of("directValue")));
    }

    static void testWTConcurrent() throws Exception {
        var repo = new InMemoryRepository<Integer, Integer>();
        var wt = new WriteThroughCache<>(50, repo);
        int numThreads = 8, opsPerThread = 200;
        var latch = new CountDownLatch(numThreads);
        var errors = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int tid = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = tid * 1000 + i;
                        wt.put(key, key * 10);
                        var fromRepo = repo.read(key);
                        if (fromRepo.isEmpty() || fromRepo.get() != key * 10)
                            errors.incrementAndGet();
                    }
                } catch (Exception e) { errors.incrementAndGet(); }
                finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        check("WT: concurrencia garantiza consistencia cache-repo", errors.get() == 0);
    }

    // ===================== WRITE-BEHIND TESTS =====================

    static void testWBPutWritesToCacheImmediately() {
        var repo = new InMemoryRepository<String, String>();
        try (var wb = new WriteBehindCache<>(3, repo)) {
            wb.put("A", "1");
            check("WB: put escribe al cache inmediatamente",
                    wb.get("A").equals(Optional.of("1")));
        }
    }

    static void testWBPutPersistsAfterFlush() throws Exception {
        var repo = new InMemoryRepository<String, String>();
        try (var wb = new WriteBehindCache<>(3, repo)) {
            wb.put("A", "1");
            wb.put("B", "2");
            wb.flush(5000);
            check("WB: datos en repo tras flush",
                    repo.read("A").equals(Optional.of("1")) &&
                    repo.read("B").equals(Optional.of("2")));
        }
    }

    static void testWBMultipleWrites() throws Exception {
        var repo = new InMemoryRepository<String, String>();
        try (var wb = new WriteBehindCache<>(10, repo)) {
            for (int i = 0; i < 10; i++)
                wb.put("key" + i, "value" + i);
            wb.flush(5000);
            boolean allPresent = true;
            for (int i = 0; i < 10; i++)
                if (!repo.read("key" + i).equals(Optional.of("value" + i)))
                    allPresent = false;
            check("WB: múltiples escrituras persisten tras flush", allPresent);
        }
    }

    // ===================== OPTIMISTIC STORE TESTS =====================

    static void testOSPutCreatesVersionOne() {
        var store = new OptimisticStore<String, String>();
        var result = store.put("key", "value");
        check("OS: put crea con versión 1",
                result.value().equals("value") && result.version() == 1L);
    }

    static void testOSGetReturnsVersioned() {
        var store = new OptimisticStore<String, String>();
        store.put("key", "value");
        var result = store.get("key");
        check("OS: get retorna valor versionado",
                result.isPresent() &&
                result.get().value().equals("value") &&
                result.get().version() == 1L);
    }

    static void testOSGetMiss() {
        var store = new OptimisticStore<String, String>();
        check("OS: get miss retorna empty",
                store.get("noexiste").equals(Optional.empty()));
    }

    static void testOSUpdateIfMatchSuccess() {
        var store = new OptimisticStore<String, String>();
        store.put("key", "v1");
        var updated = store.updateIfMatch("key", "v2", 1L);
        check("OS: updateIfMatch exitoso incrementa versión",
                updated.value().equals("v2") && updated.version() == 2L);
    }

    static void testOSUpdateIfMatchVersionMismatch() {
        var store = new OptimisticStore<String, String>();
        store.put("key", "v1");
        store.updateIfMatch("key", "v2", 1L);
        boolean threw = false;
        try {
            store.updateIfMatch("key", "v3", 1L); // versión obsoleta
        } catch (OptimisticLockException e) { threw = true; }
        check("OS: updateIfMatch con versión incorrecta lanza OptimisticLockException", threw);
    }

    static void testOSUpdateIfMatchKeyNotFound() {
        var store = new OptimisticStore<String, String>();
        boolean threw = false;
        try {
            store.updateIfMatch("noexiste", "v1", 1L);
        } catch (IllegalStateException e) { threw = true; }
        check("OS: updateIfMatch en clave inexistente lanza IllegalStateException", threw);
    }

    static void testOSSequentialUpdates() {
        var store = new OptimisticStore<String, String>();
        store.put("key", "v1");
        store.updateIfMatch("key", "v2", 1L);
        store.updateIfMatch("key", "v3", 2L);
        var result = store.updateIfMatch("key", "v4", 3L);
        check("OS: actualizaciones sucesivas incrementan versión",
                result.value().equals("v4") && result.version() == 4L);
    }

    static void testOSConcurrentConflict() throws Exception {
        var store = new OptimisticStore<String, String>();
        store.put("shared", "initial");

        var barrier = new CyclicBarrier(2);
        var successCount = new AtomicInteger(0);
        var failCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                var versioned = store.get("shared").orElseThrow();
                long readVersion = versioned.version();
                barrier.await(5, TimeUnit.SECONDS);
                store.updateIfMatch("shared",
                        "updated-by-" + Thread.currentThread().getName(), readVersion);
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                failCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet(); // should not happen
            }
        };

        var t1 = new Thread(task, "thread-1");
        var t2 = new Thread(task, "thread-2");
        t1.start(); t2.start();
        t1.join(10000); t2.join(10000);

        var finalV = store.get("shared").orElseThrow();
        check("OS Checkpoint: un hilo escribe, otro falla, versión final = 2",
                successCount.get() == 1 && failCount.get() == 1 &&
                finalV.version() == 2L);
    }

    static void testOSConcurrentWithRetry() throws Exception {
        var store = new OptimisticStore<String, String>();
        store.put("counter", "0");

        int numThreads = 10, incrementsPerThread = 50;
        var latch = new CountDownLatch(numThreads);
        var executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        boolean success = false;
                        while (!success) {
                            var current = store.get("counter").orElseThrow();
                            int val = Integer.parseInt(current.value());
                            try {
                                store.updateIfMatch("counter",
                                        String.valueOf(val + 1), current.version());
                                success = true;
                            } catch (OptimisticLockException e) { /* retry */ }
                        }
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        var finalVal = store.get("counter").orElseThrow();
        int expected = numThreads * incrementsPerThread;
        check("OS: retry pattern con " + numThreads + " hilos × " + incrementsPerThread +
                " incrementos = " + expected,
                finalVal.value().equals(String.valueOf(expected)) &&
                finalVal.version() == expected + 1);
    }

    // ===================== HELPERS =====================

    static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    static void check(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ✓ " + testName);
        } else {
            failed++;
            System.out.println("  ✗ FAILED: " + testName);
        }
    }
}

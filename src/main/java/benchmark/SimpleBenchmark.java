package benchmark;

import cache.LRUCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Benchmark simplificado que compara el rendimiento de acceso a datos
 * con y sin cache LRU bajo diferentes tamaños de cache.
 *
 * <p>Simula el comportamiento de JMH midiendo throughput (ops/ms)
 * con warmup y múltiples iteraciones de medición.</p>
 *
 * <p>Ejecución: {@code java -cp build/classes benchmark.SimpleBenchmark}</p>
 */
public class SimpleBenchmark {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;
    private static final long ITERATION_DURATION_MS = 1000;
    private static final int[] CACHE_SIZES = {100, 1000, 10000};

    public static void main(String[] args) {
        System.out.println("=" .repeat(80));
        System.out.println("  CACHE BENCHMARK — Cache vs Sin Cache");
        System.out.println("  Warmup: " + WARMUP_ITERATIONS + " iter, Measurement: "
                + MEASURE_ITERATIONS + " iter × " + ITERATION_DURATION_MS + "ms");
        System.out.println("=" .repeat(80));
        System.out.printf("%-12s %-20s %15s %15s %15s%n",
                "cacheSize", "Benchmark", "Avg (ops/ms)", "Min (ops/ms)", "Max (ops/ms)");
        System.out.println("-" .repeat(80));

        for (int cacheSize : CACHE_SIZES) {
            runBenchmark(cacheSize);
        }

        System.out.println("=" .repeat(80));
        System.out.println("\nNota: el benchmark con cache incluye el overhead del LRU");
        System.out.println("(locks, lista doblemente enlazada). Sin cache es un HashMap.get()");
        System.out.println("directo. En escenarios reales con I/O de disco o red, el cache");
        System.out.println("ofrece mejoras de órdenes de magnitud.");
    }

    private static void runBenchmark(int cacheSize) {
        // Setup
        var lruCache = new LRUCache<Integer, String>(cacheSize);
        var directMap = new HashMap<Integer, String>();
        int dataSize = cacheSize * 10;

        for (int i = 0; i < dataSize; i++) {
            directMap.put(i, "value-" + i);
        }

        // Benchmark: withCache
        var withCacheResults = measureThroughput(() -> {
            var rng = new Random(42);
            return () -> {
                int key = rng.nextInt(dataSize);
                return lruCache.get(key).orElseGet(() -> {
                    String v = directMap.get(key);
                    lruCache.put(key, v);
                    return v;
                });
            };
        });

        // Reset cache for fair comparison
        var freshCache = new LRUCache<Integer, String>(cacheSize);

        // Benchmark: withoutCache
        var withoutCacheResults = measureThroughput(() -> {
            var rng = new Random(42);
            return () -> directMap.get(rng.nextInt(dataSize));
        });

        System.out.printf("%-12d %-20s %15.2f %15.2f %15.2f%n",
                cacheSize, "withCache",
                withCacheResults[0], withCacheResults[1], withCacheResults[2]);
        System.out.printf("%-12d %-20s %15.2f %15.2f %15.2f%n",
                cacheSize, "withoutCache",
                withoutCacheResults[0], withoutCacheResults[1], withoutCacheResults[2]);
        System.out.println();
    }

    @FunctionalInterface
    interface BenchmarkOp {
        Object run();
    }

    @FunctionalInterface
    interface BenchmarkFactory {
        BenchmarkOp create();
    }

    /**
     * Mide throughput en ops/ms.
     * @return [avg, min, max]
     */
    private static double[] measureThroughput(BenchmarkFactory factory) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            var op = factory.create();
            long end = System.currentTimeMillis() + ITERATION_DURATION_MS;
            while (System.currentTimeMillis() < end) {
                op.run();
            }
        }

        // Measurement
        double[] results = new double[MEASURE_ITERATIONS];
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            var op = factory.create();
            long ops = 0;
            long start = System.currentTimeMillis();
            long end = start + ITERATION_DURATION_MS;
            while (System.currentTimeMillis() < end) {
                op.run();
                ops++;
            }
            long elapsed = System.currentTimeMillis() - start;
            results[i] = (double) ops / elapsed; // ops/ms
        }

        double sum = 0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (double r : results) {
            sum += r;
            min = Math.min(min, r);
            max = Math.max(max, r);
        }
        return new double[]{sum / results.length, min, max};
    }
}

package benchmark;

import cache.LRUCache;
import org.openjdk.jmh.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark JMH que compara el rendimiento de acceso a datos
 * con y sin cache LRU bajo diferentes tamaños de cache.
 *
 * <p>El dataset es 10x el tamaño del cache para simular misses.
 * Se espera mayor throughput con cache cuando hay alta localidad
 * temporal (distribución Zipf).</p>
 *
 * <p>Ejecución:</p>
 * <pre>
 * mvn clean package
 * java -jar target/benchmarks.jar CacheBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CacheBenchmark {

    @Param({"100", "1000", "10000"})
    private int cacheSize;

    private LRUCache<Integer, String> lruCache;
    private Map<Integer, String> directMap; // simula DB
    private Random rng;

    @Setup
    public void setup() {
        lruCache = new LRUCache<>(cacheSize);
        directMap = new HashMap<>();
        rng = new Random(42);

        // Poblar datos: 10x el tamaño del cache para simular misses
        for (int i = 0; i < cacheSize * 10; i++)
            directMap.put(i, "value-" + i);
    }

    @Benchmark
    public String withCache() {
        int key = rng.nextInt(cacheSize * 10);
        return lruCache.get(key).orElseGet(() -> {
            String v = directMap.get(key);
            lruCache.put(key, v);
            return v;
        });
    }

    @Benchmark
    public String withoutCache() {
        return directMap.get(rng.nextInt(cacheSize * 10));
    }
}

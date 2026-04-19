package cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Write-behind (write-back) cache: escritura inmediata al cache,
 * persistencia asíncrona al repositorio.
 *
 * <h3>Comportamiento:</h3>
 * {@code put()} escribe inmediatamente al cache LRU y encola la escritura
 * al repositorio en una {@link BlockingQueue}. Un hilo dedicado consume
 * la cola y persiste los datos de forma asíncrona.
 *
 * <h3>Riesgo de pérdida de datos:</h3>
 * Si el proceso termina antes de que el hilo de escritura procese toda la cola,
 * los datos encolados se pierden. Mitigación: invocar {@code close()} o
 * {@code flush()} en shutdown hooks.
 *
 * <h3>Thread-safety:</h3>
 * - El cache LRU es thread-safe internamente.
 * - La cola de escritura ({@link LinkedBlockingQueue}) es thread-safe.
 * - El hilo de escritura es un single-thread executor dedicado.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public class WriteBehindCache<K, V> implements AutoCloseable {

    private final LRUCache<K, V> cache;
    private final CacheRepository<K, V> repo;
    private final BlockingQueue<Map.Entry<K, V>> writeQueue =
            new LinkedBlockingQueue<>();
    private final ExecutorService writer =
            Executors.newSingleThreadExecutor();

    /**
     * Crea un WriteBehindCache e inicia el hilo de escritura asíncrona.
     *
     * @param cap  capacidad máxima del cache LRU
     * @param repo repositorio de persistencia
     */
    public WriteBehindCache(int cap, CacheRepository<K, V> repo) {
        this.cache = new LRUCache<>(cap);
        this.repo = repo;

        // Hilo de escritura asíncrona al repositorio
        writer.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var entry = writeQueue.take();
                    repo.write(entry.getKey(), entry.getValue());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * Escribe un valor: inmediatamente al cache, asíncronamente al repositorio.
     *
     * @param key   clave
     * @param value valor
     */
    public void put(K key, V value) {
        cache.put(key, value);
        writeQueue.offer(Map.entry(key, value)); // asíncrono
    }

    /**
     * Obtiene un valor desde el cache.
     * Nota: si el valor fue evictado del cache pero aún no persistido,
     * esta operación retornará empty.
     *
     * @param key clave a buscar
     * @return Optional con el valor si está en cache
     */
    public Optional<V> get(K key) {
        return cache.get(key);
    }

    /**
     * Espera a que todas las escrituras pendientes sean procesadas.
     * Útil para tests y shutdown hooks.
     *
     * @param timeoutMs tiempo máximo de espera en milisegundos
     * @throws InterruptedException si el hilo es interrumpido durante la espera
     */
    public void flush(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!writeQueue.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    /**
     * Retorna el número de escrituras pendientes en la cola.
     */
    public int pendingWrites() {
        return writeQueue.size();
    }

    /**
     * Cierra el hilo de escritura. Las escrituras pendientes pueden perderse.
     * Para un cierre limpio, invocar {@code flush()} antes de {@code close()}.
     */
    @Override
    public void close() {
        writer.shutdownNow();
    }
}

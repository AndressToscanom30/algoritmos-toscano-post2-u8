package cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de {@link CacheRepository} para pruebas.
 * Usa {@link ConcurrentHashMap} para thread-safety.
 * Opcionalmente simula latencia de I/O para benchmarks realistas.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public class InMemoryRepository<K, V> implements CacheRepository<K, V> {

    private final Map<K, V> store = new ConcurrentHashMap<>();
    private final long simulatedLatencyMs;

    /**
     * Crea un repositorio en memoria sin latencia simulada.
     */
    public InMemoryRepository() {
        this(0);
    }

    /**
     * Crea un repositorio en memoria con latencia simulada.
     *
     * @param simulatedLatencyMs milisegundos de latencia por operación
     */
    public InMemoryRepository(long simulatedLatencyMs) {
        this.simulatedLatencyMs = simulatedLatencyMs;
    }

    @Override
    public void write(K key, V value) {
        simulateLatency();
        store.put(key, value);
    }

    @Override
    public Optional<V> read(K key) {
        simulateLatency();
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void delete(K key) {
        simulateLatency();
        store.remove(key);
    }

    /**
     * Retorna el número de elementos almacenados.
     */
    public int size() {
        return store.size();
    }

    /**
     * Verifica si una clave existe en el repositorio.
     */
    public boolean containsKey(K key) {
        return store.containsKey(key);
    }

    private void simulateLatency() {
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

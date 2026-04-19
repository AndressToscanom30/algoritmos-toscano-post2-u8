package cache;

import java.util.Optional;

/**
 * Write-through cache: escritura simultánea a cache y repositorio.
 *
 * <h3>Garantía de consistencia:</h3>
 * Si {@code put()} retorna sin excepción, el dato está tanto en el cache
 * como en el repositorio de persistencia. Se escribe primero al repositorio
 * (fuente de verdad) y luego al cache.
 *
 * <h3>Comportamiento en cache miss:</h3>
 * Cuando {@code get()} no encuentra la clave en el cache, consulta al
 * repositorio y, si el valor existe, lo carga en el cache (cache-aside pattern).
 *
 * <h3>Thread-safety:</h3>
 * Hereda la thread-safety del {@link LRUCache} subyacente. Las operaciones
 * de lectura/escritura al repositorio no están sincronizadas adicionalmente,
 * delegando esa responsabilidad a la implementación del repositorio.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public class WriteThroughCache<K, V> {

    private final LRUCache<K, V> cache;
    private final CacheRepository<K, V> repo;

    /**
     * Crea un WriteThroughCache con la capacidad y repositorio especificados.
     *
     * @param capacity capacidad máxima del cache LRU
     * @param repo     repositorio de persistencia
     */
    public WriteThroughCache(int capacity, CacheRepository<K, V> repo) {
        this.cache = new LRUCache<>(capacity);
        this.repo = repo;
    }

    /**
     * Obtiene un valor. Primero busca en cache; en caso de miss,
     * busca en el repositorio y puebla el cache si lo encuentra.
     *
     * @param key clave a buscar
     * @return Optional con el valor si existe en cache o repositorio
     */
    public Optional<V> get(K key) {
        Optional<V> cached = cache.get(key);
        if (cached.isPresent()) return cached;

        // Cache miss: leer desde repositorio y poblar cache
        Optional<V> fromRepo = repo.read(key);
        fromRepo.ifPresent(v -> cache.put(key, v));
        return fromRepo;
    }

    /**
     * Escribe un valor de forma write-through: primero al repositorio
     * (fuente de verdad), luego al cache.
     *
     * @param key   clave
     * @param value valor a escribir
     */
    public void put(K key, V value) {
        repo.write(key, value); // primero la fuente de verdad
        cache.put(key, value);  // luego el cache
    }

    /**
     * Invalida una entrada del cache. El valor persiste en el repositorio
     * pero se marca como inválido en el cache.
     *
     * @param key clave a invalidar
     */
    public void invalidate(K key) {
        cache.put(key, null); // marcar como inválido (tombstone)
    }

    /**
     * Retorna el tamaño actual del cache.
     */
    public int cacheSize() {
        return cache.size();
    }
}

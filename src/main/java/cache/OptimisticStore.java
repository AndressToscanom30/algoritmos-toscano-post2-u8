package cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén con control de concurrencia optimista basado en versiones.
 * Simula el comportamiento de ETag en APIs REST.
 *
 * <h3>Invariantes:</h3>
 * <ul>
 *   <li>La versión se incrementa en cada escritura exitosa.</li>
 *   <li>Las escrituras condicionales ({@code updateIfMatch}) fallan
 *       atómicamente si la versión actual no coincide con la esperada.</li>
 *   <li>La atomicidad se garantiza mediante {@link ConcurrentHashMap#compute}.</li>
 * </ul>
 *
 * <h3>Analogía HTTP:</h3>
 * <ul>
 *   <li>{@code get()} → GET con respuesta que incluye ETag</li>
 *   <li>{@code put()} → PUT sin If-Match (creación incondicional)</li>
 *   <li>{@code updateIfMatch()} → PUT con If-Match (actualización condicional)</li>
 * </ul>
 *
 * <h3>Thread-safety:</h3>
 * Todas las operaciones son thread-safe gracias al uso de
 * {@link ConcurrentHashMap} y su método atómico {@code compute()}.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public class OptimisticStore<K, V> {

    /**
     * Record que encapsula un valor junto con su número de versión.
     *
     * @param value   el valor almacenado
     * @param version número de versión (se incrementa en cada escritura)
     * @param <V>     tipo del valor
     */
    public record Versioned<V>(V value, long version) {}

    private final ConcurrentHashMap<K, Versioned<V>> store =
            new ConcurrentHashMap<>();

    /**
     * Lee el valor y su versión actual.
     *
     * @param key clave a buscar
     * @return Optional con el valor versionado si existe
     */
    public Optional<Versioned<V>> get(K key) {
        return Optional.ofNullable(store.get(key));
    }

    /**
     * Escritura sin condición (primera vez o sobrescritura incondicional).
     * Asigna versión 1 al nuevo valor.
     *
     * @param key   clave
     * @param value valor a almacenar
     * @return el valor versionado creado (versión 1)
     */
    public Versioned<V> put(K key, V value) {
        var versioned = new Versioned<>(value, 1L);
        store.put(key, versioned);
        return versioned;
    }

    /**
     * Escritura condicional: solo se ejecuta si la versión actual coincide
     * con {@code expectedVersion}. Simula el header If-Match de HTTP.
     *
     * <p>La operación es atómica gracias a {@code ConcurrentHashMap.compute()}.</p>
     *
     * @param key             clave a actualizar
     * @param newValue        nuevo valor
     * @param expectedVersion versión esperada (debe coincidir con la actual)
     * @return el valor versionado actualizado (versión incrementada)
     * @throws IllegalStateException    si la clave no existe
     * @throws OptimisticLockException  si la versión actual != expectedVersion
     */
    public Versioned<V> updateIfMatch(K key, V newValue, long expectedVersion) {
        return store.compute(key, (k, current) -> {
            if (current == null)
                throw new IllegalStateException("Key not found: " + k);
            if (current.version() != expectedVersion)
                throw new OptimisticLockException(
                        "Expected v" + expectedVersion +
                                " but found v" + current.version());
            return new Versioned<>(newValue, current.version() + 1);
        });
    }

    /**
     * Retorna el número de entradas en el almacén.
     */
    public int size() {
        return store.size();
    }
}

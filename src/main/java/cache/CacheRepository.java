package cache;

import java.util.Optional;

/**
 * Puerto de persistencia para el sistema de caching.
 * Define las operaciones básicas de lectura, escritura y eliminación
 * que un repositorio de datos debe soportar.
 *
 * <p>Las implementaciones pueden representar bases de datos, archivos,
 * servicios remotos, o almacenes en memoria para pruebas.</p>
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public interface CacheRepository<K, V> {

    /**
     * Persiste un par clave-valor en el almacén.
     *
     * @param key   clave
     * @param value valor a persistir
     */
    void write(K key, V value);

    /**
     * Lee un valor desde el almacén de persistencia.
     *
     * @param key clave a buscar
     * @return Optional con el valor si existe, Optional.empty() si no
     */
    Optional<V> read(K key);

    /**
     * Elimina un par clave-valor del almacén de persistencia.
     *
     * @param key clave a eliminar
     */
    void delete(K key);
}

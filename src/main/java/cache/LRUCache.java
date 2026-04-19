package cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU Cache thread-safe usando HashMap + lista doblemente enlazada.
 * <p>
 * Las operaciones {@code get} y {@code put} son O(1) amortizado.
 * </p>
 *
 * <h3>Invariantes:</h3>
 * <ul>
 *   <li>{@code map.size() == tamaño de la lista enlazada}</li>
 *   <li>{@code map.size() <= capacity}</li>
 *   <li>El nodo más recientemente usado está al frente (después de head)</li>
 *   <li>El nodo menos recientemente usado está al final (antes de tail)</li>
 * </ul>
 *
 * <h3>Thread-safety:</h3>
 * Se usa {@link ReentrantReadWriteLock} para garantizar acceso seguro
 * desde múltiples hilos. Tanto {@code get} como {@code put} adquieren
 * el write lock porque ambos modifican la estructura de la lista.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
public class LRUCache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> map = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Crea un LRU Cache con la capacidad especificada.
     *
     * @param capacity capacidad máxima del cache (debe ser > 0)
     * @throws IllegalArgumentException si capacity <= 0
     */
    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be > 0");
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Obtiene el valor asociado a la clave y marca el elemento como
     * recientemente usado (lo mueve al frente de la lista).
     *
     * @param key clave a buscar
     * @return Optional con el valor si existe, Optional.empty() si no
     */
    public Optional<V> get(K key) {
        lock.writeLock().lock(); // write lock: mueve el nodo
        try {
            Node<K, V> node = map.get(key);
            if (node == null) return Optional.empty();
            moveToFront(node);
            return Optional.of(node.value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Inserta o actualiza un par clave-valor en el cache.
     * Si la clave ya existe, actualiza el valor y mueve al frente.
     * Si el cache está lleno, evicta el elemento menos recientemente usado.
     *
     * @param key   clave
     * @param value valor
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            if (map.containsKey(key)) {
                Node<K, V> node = map.get(key);
                node.value = value;
                moveToFront(node);
            } else {
                if (map.size() == capacity) evict();
                var node = new Node<>(key, value);
                map.put(key, node);
                addToFront(node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evicta el nodo menos recientemente usado (el anterior a tail).
     */
    private void evict() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        map.remove(lru.key);
    }

    /**
     * Mueve un nodo existente al frente de la lista.
     */
    private void moveToFront(Node<K, V> n) {
        removeNode(n);
        addToFront(n);
    }

    /**
     * Agrega un nodo nuevo al frente de la lista (después de head).
     */
    private void addToFront(Node<K, V> n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }

    /**
     * Remueve un nodo de la lista doblemente enlazada.
     */
    private void removeNode(Node<K, V> n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    /**
     * Retorna el número actual de elementos en el cache.
     *
     * @return tamaño del cache
     */
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retorna la capacidad máxima del cache.
     *
     * @return capacidad
     */
    public int getCapacity() {
        return capacity;
    }
}

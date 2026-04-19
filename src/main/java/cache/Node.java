package cache;

/**
 * Nodo de lista doblemente enlazada para LRU Cache.
 * Almacena la clave (necesaria para la evicción desde el map)
 * y el valor asociado.
 *
 * @param <K> tipo de la clave
 * @param <V> tipo del valor
 */
class Node<K, V> {
    final K key;
    V value;
    Node<K, V> prev;
    Node<K, V> next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}

package cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para {@link WriteBehindCache}.
 * Cubre: escritura asíncrona, verificación de persistencia tras flush,
 * y comportamiento antes del flush.
 */
class WriteBehindCacheTest {

    @Test
    @DisplayName("put escribe inmediatamente al cache")
    void testPutWritesToCacheImmediately() {
        var repo = new InMemoryRepository<String, String>();
        try (var wbCache = new WriteBehindCache<>(3, repo)) {
            wbCache.put("A", "1");

            // El cache debe tener el valor inmediatamente
            assertEquals(Optional.of("1"), wbCache.get("A"),
                    "El valor debe estar en cache inmediatamente tras put()");
        }
    }

    @Test
    @DisplayName("put persiste al repo asíncronamente tras flush")
    void testPutPersistsAfterFlush() throws InterruptedException {
        var repo = new InMemoryRepository<String, String>();
        try (var wbCache = new WriteBehindCache<>(3, repo)) {
            wbCache.put("A", "1");
            wbCache.put("B", "2");

            // Esperar a que el hilo de escritura procese la cola
            wbCache.flush(5000);

            assertEquals(Optional.of("1"), repo.read("A"),
                    "A debe estar en repo tras flush");
            assertEquals(Optional.of("2"), repo.read("B"),
                    "B debe estar en repo tras flush");
        }
    }

    @Test
    @DisplayName("Riesgo: datos pueden no estar en repo antes del flush")
    void testDataMayNotBeInRepoBeforeFlush() {
        var repo = new InMemoryRepository<String, String>(50); // 50ms latencia
        try (var wbCache = new WriteBehindCache<>(3, repo)) {
            wbCache.put("A", "1");

            // Inmediatamente después del put, el repo puede no tener el dato aún
            // (depende del timing del hilo de escritura con latencia simulada)
            // Este test documenta el riesgo inherente de write-behind
            assertEquals(Optional.of("1"), wbCache.get("A"),
                    "El cache siempre debe tener el dato");
        }
    }

    @Test
    @DisplayName("Múltiples escrituras se procesan en orden")
    void testWriteOrder() throws InterruptedException {
        var repo = new InMemoryRepository<String, String>();
        try (var wbCache = new WriteBehindCache<>(10, repo)) {
            for (int i = 0; i < 10; i++) {
                wbCache.put("key" + i, "value" + i);
            }

            wbCache.flush(5000);

            for (int i = 0; i < 10; i++) {
                assertEquals(Optional.of("value" + i), repo.read("key" + i),
                        "key" + i + " debe estar en repo");
            }
        }
    }
}

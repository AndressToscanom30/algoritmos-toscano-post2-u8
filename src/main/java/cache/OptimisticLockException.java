package cache;

/**
 * Excepción lanzada cuando una escritura condicional falla
 * porque la versión actual del recurso no coincide con la esperada.
 *
 * <p>Análoga al código HTTP 412 Precondition Failed cuando
 * el header If-Match no coincide con el ETag actual.</p>
 */
public class OptimisticLockException extends RuntimeException {

    /**
     * Crea una nueva excepción con el mensaje descriptivo.
     *
     * @param msg mensaje describiendo la discrepancia de versión
     */
    public OptimisticLockException(String msg) {
        super(msg);
    }
}

package com.berzantas.kanban.common;

/**
 * Thrown when a business rule is violated that is not expressible as a field-level
 * constraint (for example, the same-team rule between a ticket and its epic). The API
 * layer maps this to HTTP 400. Extends {@link RuntimeException} so that throwing it rolls
 * back the current transaction.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}

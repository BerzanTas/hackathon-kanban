package com.berzantas.kanban.common;

/**
 * Thrown on a uniqueness violation or when a delete is blocked by existing references.
 * The API layer maps this to HTTP 409. Extends {@link RuntimeException} so that throwing
 * it rolls back the current transaction.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

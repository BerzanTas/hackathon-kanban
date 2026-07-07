package com.berzantas.kanban.common;

/**
 * Thrown when a referenced entity does not exist. The API layer maps this to HTTP 404.
 * Extends {@link RuntimeException} so that throwing it rolls back the current transaction.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

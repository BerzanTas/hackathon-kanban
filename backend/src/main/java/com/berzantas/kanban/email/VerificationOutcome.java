package com.berzantas.kanban.email;

/** Result of attempting to verify an email-verification token. */
public enum VerificationOutcome {
    VERIFIED,
    EXPIRED,
    INVALID
}

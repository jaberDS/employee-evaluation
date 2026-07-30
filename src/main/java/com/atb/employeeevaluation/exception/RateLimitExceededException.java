package com.atb.employeeevaluation.exception;

/** Trop de tentatives sur un endpoint sensible. Remonte en 429. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}

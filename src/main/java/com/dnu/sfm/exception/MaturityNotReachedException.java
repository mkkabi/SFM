package com.dnu.sfm.exception;

/**
 * Thrown when a withdrawal from a deposit account is attempted
 * before the deposit has reached its maturity date.
 */
public class MaturityNotReachedException extends RuntimeException {
    public MaturityNotReachedException(String message) {
        super(message);
    }
}

package com.dnu.sfm.exception;

/**
 * Thrown when new funds are added to a deposit account that has already
 * reached its maturity date.
 * <p>
 * Once the maturity date is reached the deposit term is over: adding money
 * would silently start a new term at the old conditions. The deposit accepts
 * no new money - the only allowed operations are withdrawing on maturity
 * and the close &amp; withdraw payout flow.
 */
public class MaturityReachedException extends RuntimeException {
    public MaturityReachedException(String message) {
        super(message);
    }
}

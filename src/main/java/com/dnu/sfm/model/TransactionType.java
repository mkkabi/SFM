package com.dnu.sfm.model;

/**
 * Direction of the money movement recorded by a {@link Transaction}.
 */
public enum TransactionType {
    /** Money added to the account balance. */
    DEPOSIT,
    /** Money removed from the account balance. */
    WITHDRAWAL
}

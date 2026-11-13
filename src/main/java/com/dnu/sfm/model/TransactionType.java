package com.dnu.sfm.model;

/**
 * Direction of the money movement recorded by a {@link Transaction}.
 */
public enum TransactionType {
    /** Money added to the account balance. */
    DEPOSIT,
    /** Money removed from the account balance. */
    WITHDRAWAL,
    /** Money moved out of the account as the outgoing leg of a transfer (debit). */
    DEBIT,
    /** Money moved into the account as the incoming leg of a transfer (credit). */
    CREDIT
}

package com.dnu.sfm.model;

/**
 * Operations a user can apply to an existing account from the update form.
 */
public enum AccountOperation {
    /** Add funds to the account balance. */
    DEPOSIT,
    /** Remove funds from the account balance. */
    WITHDRAW,
    /** Prolong a deposit term by extending its maturity date. */
    EXTEND_MATURITY
}

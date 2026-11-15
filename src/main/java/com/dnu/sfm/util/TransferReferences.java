package com.dnu.sfm.util;

import java.util.Locale;
import java.util.UUID;

/**
 * Generates the reference shared by the two legs (DEBIT + CREDIT) of one
 * transfer. The reference is stored on both transaction rows so the transfer
 * history page can pair them into a single movement.
 */
public final class TransferReferences {

    /** Prefix marking a transfer reference, e.g. {@code TRF-3F9A2C1B8D4E}. */
    private static final String PREFIX = "TRF-";

    /** Number of random hexadecimal characters after the prefix. */
    private static final int RANDOM_PART_LENGTH = 12;

    private TransferReferences() {
    }

    /**
     * @return a new unique reference like {@code TRF-3F9A2C1B8D4E}
     */
    public static String generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return PREFIX + uuid.substring(0, RANDOM_PART_LENGTH);
    }
}

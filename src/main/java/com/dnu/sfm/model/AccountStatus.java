package com.dnu.sfm.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
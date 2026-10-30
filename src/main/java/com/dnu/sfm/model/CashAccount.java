package com.dnu.sfm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "cash_accounts")
public class CashAccount extends Account {

    public CashAccount() {
        super();
    }

}
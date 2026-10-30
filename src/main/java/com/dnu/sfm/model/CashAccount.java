package com.dnu.sfm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cash_accounts")
public class CashAccount extends Account {

    public CashAccount() {
        super();
    }

}
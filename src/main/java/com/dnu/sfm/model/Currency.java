package com.dnu.sfm.model;

import lombok.Getter;

@Getter
public enum Currency {
    USD("united-states"),
//    EUR("euro-area"),      // StatBureau використовує саме "euro-area"
    EUR("eurozone"),      // StatBureau використовує саме "euro-area"
    UAH("ukraine");

    private final String countrySlug;

    Currency(String countrySlug) {
        this.countrySlug = countrySlug;
    }
}
package com.dnu.sfm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRate {

    @JsonProperty("r030")
    private Integer numericCode;

    @JsonProperty("txt")
    private String currencyName;

    @JsonProperty("rate")
    private BigDecimal rate;

    @JsonProperty("cc")
    private String currencyCode;

    @JsonProperty("exchangedate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy")
    private LocalDate exchangeDate;

    @JsonProperty("special")
    private String special;

    public ExchangeRate() {
    }

    public ExchangeRate(Integer numericCode, String currencyName, BigDecimal rate,
                        String currencyCode, LocalDate exchangeDate, String special) {
        this.numericCode = numericCode;
        this.currencyName = currencyName;
        this.rate = rate;
        this.currencyCode = currencyCode;
        this.exchangeDate = exchangeDate;
        this.special = special;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExchangeRate that)) return false;
        return Objects.equals(numericCode, that.numericCode) &&
                Objects.equals(currencyCode, that.currencyCode) &&
                Objects.equals(exchangeDate, that.exchangeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numericCode, currencyCode, exchangeDate);
    }

    @Override
    public String toString() {
        return "ExchangeRate{" +
                "numericCode=" + numericCode +
                ", currencyName='" + currencyName + '\'' +
                ", rate=" + rate +
                ", currencyCode='" + currencyCode + '\'' +
                ", exchangeDate=" + exchangeDate +
                ", special='" + special + '\'' +
                '}';
    }
}
package com.dnu.sfm.repository;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatBureauYearlyInflationRepository extends JpaRepository<StatBureauYearlyInflation, Long> {

    /** The full stored yearly history of one currency's economy, any order. */
    List<StatBureauYearlyInflation> findByCurrency(Currency currency);

    /** Stored records of one currency for one calendar year (0 or 1 row). */
    List<StatBureauYearlyInflation> findByCurrencyAndInflationYear(Currency currency, Integer inflationYear);

    /** Total number of stored records of one currency (full cached history). */
    long countByCurrency(Currency currency);

    /** Number of stored records of one currency for one calendar year. */
    long countByCurrencyAndInflationYear(Currency currency, Integer inflationYear);

    /** The newest stored year of one currency, for the data-freshness hint. */
    Optional<StatBureauYearlyInflation> findFirstByCurrencyOrderByInflationYearDesc(Currency currency);
}

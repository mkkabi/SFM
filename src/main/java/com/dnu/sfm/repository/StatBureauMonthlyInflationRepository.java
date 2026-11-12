package com.dnu.sfm.repository;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauMonthlyInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatBureauMonthlyInflationRepository extends JpaRepository<StatBureauMonthlyInflation, Long> {

    /** The full stored history of one currency's economy, any order. */
    List<StatBureauMonthlyInflation> findByCurrency(Currency currency);

    /** All stored months of one currency inside a date range (inclusive), oldest first. */
    List<StatBureauMonthlyInflation> findByCurrencyAndRecordDateBetweenOrderByRecordDateAsc(
            Currency currency, LocalDate start, LocalDate end);

    /** Total number of stored records of one currency (full cached history). */
    long countByCurrency(Currency currency);

    /** Number of stored months of one currency inside a date range (inclusive). */
    long countByCurrencyAndRecordDateBetween(Currency currency, LocalDate start, LocalDate end);

    /** The newest stored month of one currency, for the data-freshness hint. */
    Optional<StatBureauMonthlyInflation> findFirstByCurrencyOrderByRecordDateDesc(Currency currency);

    /** The newest stored month of one currency inside a date range, for the headline banner. */
    Optional<StatBureauMonthlyInflation> findFirstByCurrencyAndRecordDateBetweenOrderByRecordDateDesc(
            Currency currency, LocalDate start, LocalDate end);
}

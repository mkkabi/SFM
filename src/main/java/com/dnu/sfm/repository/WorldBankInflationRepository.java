package com.dnu.sfm.repository;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.WorldBankInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorldBankInflationRepository extends JpaRepository<WorldBankInflation, Long> {

    /** All stored World Bank records (one per tracked currency) for one calendar year. */
    List<WorldBankInflation> findByInflationYear(Integer inflationYear);

    /** Total number of stored records for one calendar year. */
    long countByInflationYear(Integer inflationYear);

    /** The stored record of one currency's economy for one calendar year, if any. */
    Optional<WorldBankInflation> findByInflationYearAndCurrency(Integer inflationYear, Currency currency);
}

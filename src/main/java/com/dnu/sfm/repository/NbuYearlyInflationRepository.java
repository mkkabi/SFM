package com.dnu.sfm.repository;

import com.dnu.sfm.model.NbuYearlyInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NbuYearlyInflationRepository extends JpaRepository<NbuYearlyInflation, Long> {

    /** All stored records (every commodity group and index type) for one calendar year. */
    List<NbuYearlyInflation> findByInflationYear(Integer inflationYear);

    /** Total number of stored records for one calendar year. */
    long countByInflationYear(Integer inflationYear);
}

package com.dnu.sfm.repository;

import com.dnu.sfm.model.NbuYearlyInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NbuYearlyInflationRepository extends JpaRepository<NbuYearlyInflation, Long> {
    Optional<NbuYearlyInflation> findByInflationYearAndTzepAndMcrd081(
            Integer inflationYear, String tzep, String mcrd081);

    boolean existsByInflationYearAndTzepAndMcrd081(
            Integer inflationYear, String tzep, String mcrd081);
}
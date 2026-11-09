package com.dnu.sfm.repository;

import com.dnu.sfm.model.NbuMonthlyInflation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NbuMonthlyInflationRepository extends JpaRepository<NbuMonthlyInflation, Long> {
    Optional<NbuMonthlyInflation> findByRecordDateAndIndexTypeAndCommodityGroup(
            LocalDate recordDate, String indexType, String commodityGroup);

    boolean existsByRecordDateAndIndexTypeAndCommodityGroup(
            LocalDate recordDate, String indexType, String commodityGroup);

    List<NbuMonthlyInflation> findByRecordDateOrderByCommodityGroupAsc(LocalDate recordDate);

    /** All records (national + every regional breakdown) stored for one month. */
    List<NbuMonthlyInflation> findByRecordDate(LocalDate recordDate);

    /** Total number of stored records (all scopes) for one month. */
    long countByRecordDate(LocalDate recordDate);
}
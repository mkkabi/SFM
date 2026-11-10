package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.NbuInflationClient;
import com.dnu.sfm.dto.NbuYearlyInflationRecordDto;
import com.dnu.sfm.model.NbuYearlyInflation;
import com.dnu.sfm.repository.NbuYearlyInflationRepository;
import com.dnu.sfm.service.NbuYearlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One NBU yearly fetch returns the full breakdown for a single calendar year
 * (all commodity groups x national CPI index types, plus regional rows and PPI
 * rows that this feature does not track), so persistence is done as a single
 * transactional bulk upsert instead of a per-row find/save loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NbuYearlyInflationServiceImpl implements NbuYearlyInflationService {

    /**
     * Only national (Ukraine-wide) CPI rows are ingested: the entity's business
     * key (year, tzep, mcrd081) has no region dimension, and regional (ku) or
     * PPI rows would collide with the national aggregates.
     */
    private static final String CPI_FAMILY = "prices_price_cpi_";

    private final NbuYearlyInflationRepository yearlyInflationRepository;
    private final NbuInflationClient nbuInflationClient;

    @Override
    public List<NbuYearlyInflation> fetchFromNbu(int year) {
        log.info("Requesting NBU yearly inflation records for {}", year);
        List<NbuYearlyInflationRecordDto> dtos =
                nbuInflationClient.getUkraineAnnualInflation(LocalDate.of(year, 1, 1));

        List<NbuYearlyInflation> entities = dtos.stream()
                // national CPI only - see the class comment for what is skipped
                .filter(NbuYearlyInflationServiceImpl::isNationalCpi)
                .map(NbuYearlyInflation::fromDto)
                .collect(Collectors.toList());

        log.info("Fetched {} NBU records for {}, ingesting {} national annual CPI rows "
                        + "(regional/PPI rows skipped)", dtos.size(), year, entities.size());
        return entities;
    }

    @Override
    @Transactional
    public int upsertYearlyRecords(List<NbuYearlyInflation> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Integer inflationYear = entities.get(0).getInflationYear();

        // One bulk load instead of a SELECT per row; last occurrence wins on the
        // (theoretically impossible) duplicate business keys already in the DB.
        Map<String, NbuYearlyInflation> byBusinessKey = new HashMap<>();
        for (NbuYearlyInflation existing : yearlyInflationRepository.findByInflationYear(inflationYear)) {
            byBusinessKey.put(businessKey(existing), existing);
        }

        for (NbuYearlyInflation incoming : entities) {
            NbuYearlyInflation existing = byBusinessKey.get(businessKey(incoming));
            if (existing != null) {
                existing.setRatePercent(incoming.getRatePercent());
                existing.setDescription(incoming.getDescription());
            } else {
                byBusinessKey.put(businessKey(incoming), incoming);
            }
        }

        yearlyInflationRepository.saveAll(byBusinessKey.values());

        log.info("Upserted {} NBU yearly inflation records for {} ({} total stored)",
                entities.size(), inflationYear, byBusinessKey.size());
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NbuYearlyInflation> getRecordsForYear(int year) {
        return yearlyInflationRepository.findByInflationYear(year).stream()
                .sorted(Comparator
                        .comparing(NbuYearlyInflation::getMcrd081,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(NbuYearlyInflation::getTzep,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByYear(int year) {
        return yearlyInflationRepository.countByInflationYear(year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NbuYearlyInflation> findHeadlineAnnualCpi(int year) {
        return yearlyInflationRepository.findByInflationYear(year).stream()
                .filter(r -> "DTPY_".equalsIgnoreCase(r.getTzep())
                        && "Total".equalsIgnoreCase(r.getMcrd081()))
                .findFirst();
    }

    /**
     * Mirrors {@link NbuYearlyInflationRecordDto#isNationalAnnualCpi()} without
     * the tzep pinning, so every national CPI row (rate AND index level) of the
     * requested year is ingested.
     */
    private static boolean isNationalCpi(NbuYearlyInflationRecordDto dto) {
        return CPI_FAMILY.equalsIgnoreCase(dto.idApi()) && dto.ku() == null;
    }

    private static String businessKey(NbuYearlyInflation entity) {
        return entity.getInflationYear() + "|"
                + entity.getTzep() + "|"
                + entity.getMcrd081();
    }
}

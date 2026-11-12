package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.StatBureauYearlyInflationClient;
import com.dnu.sfm.dto.YearlyInflationStatBureauRecordDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;
import com.dnu.sfm.repository.StatBureauYearlyInflationRepository;
import com.dnu.sfm.service.StatBureauYearlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One StatBureau annual request answers with a single calendar-year rate for
 * one economy, so the persistence layer is keyed by the natural business key
 * (inflation year, currency): a re-fetch of the same year updates the stored
 * row in place instead of duplicating it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatBureauYearlyInflationServiceImpl implements StatBureauYearlyInflationService {

    private final StatBureauYearlyInflationRepository statBureauYearlyInflationRepository;
    private final StatBureauYearlyInflationClient statBureauYearlyInflationClient;

    @Override
    public List<StatBureauYearlyInflation> fetchFromStatBureau(Currency currency, int year) {
        log.info("Requesting StatBureau annual inflation for {} {}", currency, year);
        YearlyInflationStatBureauRecordDto dto = statBureauYearlyInflationClient.getAnnualInflation(currency, year);

        if (dto == null) {
            log.warn("StatBureau returned no annual inflation data for {} {}", currency, year);
            return Collections.emptyList();
        }

        // Converted one by one so a malformed record fails loudly.
        List<StatBureauYearlyInflation> entities = List.of(StatBureauYearlyInflation.fromDto(dto, currency));

        log.info("Fetched {} StatBureau annual inflation record for {} {} (rate {})",
                entities.size(), currency, year, entities.get(0).getRatePercent());
        return entities;
    }

    @Override
    @Transactional
    public int upsertYearlyRecords(List<StatBureauYearlyInflation> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Currency currency = entities.get(0).getCurrency();

        // One bulk load instead of a SELECT per row; last occurrence wins on the
        // (theoretically impossible) duplicate business keys already in the DB.
        Map<String, StatBureauYearlyInflation> byBusinessKey = new HashMap<>();
        for (StatBureauYearlyInflation existing : statBureauYearlyInflationRepository.findByCurrency(currency)) {
            byBusinessKey.put(businessKey(existing), existing);
        }

        for (StatBureauYearlyInflation incoming : entities) {
            StatBureauYearlyInflation existing = byBusinessKey.get(businessKey(incoming));
            if (existing != null) {
                existing.setRatePercent(incoming.getRatePercent());
            } else {
                byBusinessKey.put(businessKey(incoming), incoming);
            }
        }

        statBureauYearlyInflationRepository.saveAll(byBusinessKey.values());

        log.info("Upserted {} StatBureau yearly inflation record(s) for {} ({} total stored)",
                entities.size(), currency, byBusinessKey.size());
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatBureauYearlyInflation> getRecordsForYear(Currency currency, int year) {
        return statBureauYearlyInflationRepository.findByCurrencyAndInflationYear(currency, year);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByYear(Currency currency, int year) {
        return statBureauYearlyInflationRepository.countByCurrencyAndInflationYear(currency, year);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCurrency(Currency currency) {
        return statBureauYearlyInflationRepository.countByCurrency(currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StatBureauYearlyInflation> findLatestRecord(Currency currency) {
        return statBureauYearlyInflationRepository.findFirstByCurrencyOrderByInflationYearDesc(currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StatBureauYearlyInflation> findHeadlineAnnualCpi(Currency currency, int year) {
        return statBureauYearlyInflationRepository.findByCurrencyAndInflationYear(currency, year).stream()
                .findFirst();
    }

    private static String businessKey(StatBureauYearlyInflation entity) {
        return entity.getInflationYear() + "|"
                + entity.getCurrency();
    }
}

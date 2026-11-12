package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.StatBureauInflationClient;
import com.dnu.sfm.dto.MonthlyInflationStatBureauRecordDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauMonthlyInflation;
import com.dnu.sfm.repository.StatBureauMonthlyInflationRepository;
import com.dnu.sfm.service.StatBureauInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One StatBureau request answers with the full available monthly history of one
 * economy (hundreds of months, newest first), so a single fetch is persisted as
 * one transactional bulk upsert keyed by (record month, currency) instead of a
 * per-row find/save loop. The view layer then slices the cached history by
 * calendar year.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatBureauInflationServiceImpl implements StatBureauInflationService {

    private final StatBureauMonthlyInflationRepository statBureauMonthlyInflationRepository;
    private final StatBureauInflationClient statBureauInflationClient;

    @Override
    public List<StatBureauMonthlyInflation> fetchFromStatBureau(Currency currency) {
        log.info("Requesting StatBureau monthly inflation history for {}", currency);
        List<MonthlyInflationStatBureauRecordDto> dtos = statBureauInflationClient.getHistoricalInflation(currency);

        // The client already skips rows with unparseable months; rows that make it
        // here are converted one by one so a malformed row fails loudly.
        List<StatBureauMonthlyInflation> entities = dtos.stream()
                .map(dto -> StatBureauMonthlyInflation.fromDto(dto, currency))
                .collect(Collectors.toList());

        log.info("Fetched {} StatBureau monthly inflation record(s) for {}", entities.size(), currency);
        return entities;
    }

    @Override
    @Transactional
    public int upsertMonthlyRecords(List<StatBureauMonthlyInflation> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Currency currency = entities.get(0).getCurrency();

        // One bulk load instead of a SELECT per row; last occurrence wins on the
        // (theoretically impossible) duplicate business keys already in the DB.
        Map<String, StatBureauMonthlyInflation> byBusinessKey = new HashMap<>();
        for (StatBureauMonthlyInflation existing : statBureauMonthlyInflationRepository.findByCurrency(currency)) {
            byBusinessKey.put(businessKey(existing), existing);
        }

        for (StatBureauMonthlyInflation incoming : entities) {
            StatBureauMonthlyInflation existing = byBusinessKey.get(businessKey(incoming));
            if (existing != null) {
                existing.setRatePercent(incoming.getRatePercent());
            } else {
                byBusinessKey.put(businessKey(incoming), incoming);
            }
        }

        statBureauMonthlyInflationRepository.saveAll(byBusinessKey.values());

        log.info("Upserted {} StatBureau monthly inflation records for {} ({} total stored)",
                entities.size(), currency, byBusinessKey.size());
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatBureauMonthlyInflation> getRecordsForYear(Currency currency, int year) {
        return statBureauMonthlyInflationRepository
                .findByCurrencyAndRecordDateBetweenOrderByRecordDateAsc(currency, yearStart(year), yearEnd(year));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByYear(Currency currency, int year) {
        return statBureauMonthlyInflationRepository
                .countByCurrencyAndRecordDateBetween(currency, yearStart(year), yearEnd(year));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCurrency(Currency currency) {
        return statBureauMonthlyInflationRepository.countByCurrency(currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StatBureauMonthlyInflation> findLatestRecord(Currency currency) {
        return statBureauMonthlyInflationRepository.findFirstByCurrencyOrderByRecordDateDesc(currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StatBureauMonthlyInflation> findHeadlineMonthlyCpi(Currency currency, int year) {
        return statBureauMonthlyInflationRepository
                .findFirstByCurrencyAndRecordDateBetweenOrderByRecordDateDesc(currency, yearStart(year), yearEnd(year));
    }

    private static LocalDate yearStart(int year) {
        return Year.of(year).atDay(1);
    }

    private static LocalDate yearEnd(int year) {
        return Year.of(year).atMonth(12).atEndOfMonth();
    }

    private static String businessKey(StatBureauMonthlyInflation entity) {
        return entity.getRecordDate() + "|"
                + entity.getCurrency();
    }
}

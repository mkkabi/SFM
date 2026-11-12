package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.WorldBankInflationClient;
import com.dnu.sfm.dto.WorldBankInflationRecordDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.WorldBankInflation;
import com.dnu.sfm.repository.WorldBankInflationRepository;
import com.dnu.sfm.service.WorldBankInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One World Bank request answers a single economy/year pair, so the service
 * asks the client once per tracked {@link Currency} (UAH, EUR, USD) and merges
 * the results. Persistence is done as a single transactional bulk upsert keyed
 * by (inflation year, currency), mirroring the NBU yearly ingest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldBankInflationServiceImpl implements WorldBankInflationService {

    private final WorldBankInflationRepository worldBankInflationRepository;
    private final WorldBankInflationClient worldBankInflationClient;

    @Override
    public List<WorldBankInflation> fetchFromWorldBank(int year) {
        log.info("Requesting World Bank annual inflation records for {}", year);

        List<WorldBankInflation> entities = new ArrayList<>();
        for (Currency currency : Currency.values()) {
            List<WorldBankInflationRecordDto> dtos =
                    worldBankInflationClient.fetchAnnualInflationRecords(currency, year);

            for (WorldBankInflationRecordDto dto : dtos) {
                // The World Bank returns an explicit null for years without data
                // (e.g. future years); skip them instead of persisting a zero rate.
                if (!dto.hasValue()) {
                    log.warn("World Bank returned no value for {} in {} - skipping", currency, year);
                    continue;
                }
                entities.add(WorldBankInflation.fromDto(dto, currency));
            }
        }

        log.info("Fetched {} World Bank annual inflation record(s) for {} across {} tracked currencies",
                entities.size(), year, Currency.values().length);
        return entities;
    }

    @Override
    @Transactional
    public int upsertAnnualRecords(List<WorldBankInflation> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Integer inflationYear = entities.get(0).getInflationYear();

        // One bulk load instead of a SELECT per row; last occurrence wins on the
        // (theoretically impossible) duplicate business keys already in the DB.
        Map<String, WorldBankInflation> byBusinessKey = new HashMap<>();
        for (WorldBankInflation existing : worldBankInflationRepository.findByInflationYear(inflationYear)) {
            byBusinessKey.put(businessKey(existing), existing);
        }

        for (WorldBankInflation incoming : entities) {
            WorldBankInflation existing = byBusinessKey.get(businessKey(incoming));
            if (existing != null) {
                existing.setRatePercent(incoming.getRatePercent());
                existing.setCountryCode(incoming.getCountryCode());
                existing.setCountryName(incoming.getCountryName());
                existing.setIndicatorCode(incoming.getIndicatorCode());
            } else {
                byBusinessKey.put(businessKey(incoming), incoming);
            }
        }

        worldBankInflationRepository.saveAll(byBusinessKey.values());

        log.info("Upserted {} World Bank annual inflation records for {} ({} total stored)",
                entities.size(), inflationYear, byBusinessKey.size());
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorldBankInflation> getRecordsForYear(int year) {
        return worldBankInflationRepository.findByInflationYear(year).stream()
                .sorted(Comparator.comparing(WorldBankInflation::getCurrency))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByYear(int year) {
        return worldBankInflationRepository.countByInflationYear(year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorldBankInflation> findHeadlineAnnualCpi(int year) {
        return worldBankInflationRepository.findByInflationYearAndCurrency(year, Currency.UAH);
    }

    private static String businessKey(WorldBankInflation entity) {
        return entity.getInflationYear() + "|"
                + entity.getCurrency();
    }
}

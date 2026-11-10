package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.NbuInflationClient;
import com.dnu.sfm.dto.NbuMonthlyInflationRecordDto;
import com.dnu.sfm.model.NbuMonthlyInflation;
import com.dnu.sfm.repository.NbuMonthlyInflationRepository;
import com.dnu.sfm.service.NbuMonthlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One NBU monthly fetch returns ~800 rows (all commodity groups x all regions x
 * several index types), so persistence is done as a single transactional bulk
 * upsert instead of a per-row find/save loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NbuMonthlyInflationServiceImpl implements NbuMonthlyInflationService {

    private static final String SCOPE_ALL = "all";

    /**
     * NBU serves CPI, core inflation and PPI in one response; PPI additionally
     * repeats "Total" once per industry branch (mcrk110). This feature tracks
     * consumer prices, so only the CPI and core-inflation families are ingested.
     * The id_api is still part of the persistence key, so enabling further
     * families later cannot overwrite existing rows.
     */
    private static final Set<String> INGESTED_INDEX_FAMILIES =
            Set.of("prices_price_cpi_", "prices_price_ci_");

    private final NbuMonthlyInflationRepository inflationRepository;
    private final NbuInflationClient nbuInflationClient;

    @Override
    public List<NbuMonthlyInflation> fetchFromNbu(LocalDate targetDate) {
        log.info("Requesting NBU monthly inflation records for {}", targetDate);
        List<NbuMonthlyInflationRecordDto> dtos = nbuInflationClient.getUkraineMonthlyInflation(targetDate);

        List<NbuMonthlyInflation> entities = dtos.stream()
                .filter(dto -> dto.idApi() != null
                        && INGESTED_INDEX_FAMILIES.contains(dto.idApi().toLowerCase(Locale.ROOT)))
                .map(NbuMonthlyInflation::fromDto)
                .collect(Collectors.toList());

        log.info("Fetched {} NBU records for {}, ingesting {} consumer-price rows (non-CPI families skipped)",
                dtos.size(), targetDate, entities.size());
        return entities;
    }

    @Override
    @Transactional
    public int upsertMonthlyRecords(List<NbuMonthlyInflation> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        LocalDate recordDate = entities.get(0).getRecordDate();

        // One bulk load instead of a SELECT per row; last occurrence wins on the
        // (theoretically impossible) duplicate business keys already in the DB.
        Map<String, NbuMonthlyInflation> byBusinessKey = new HashMap<>();
        for (NbuMonthlyInflation existing : inflationRepository.findByRecordDate(recordDate)) {
            byBusinessKey.put(businessKey(existing), existing);
        }

        for (NbuMonthlyInflation incoming : entities) {
            NbuMonthlyInflation existing = byBusinessKey.get(businessKey(incoming));
            if (existing != null) {
                existing.setRatePercent(incoming.getRatePercent());
                existing.setIdApi(incoming.getIdApi());
            } else {
                byBusinessKey.put(businessKey(incoming), incoming);
            }
        }

        inflationRepository.saveAll(byBusinessKey.values());

        log.info("Upserted {} NBU monthly inflation records for {} ({} total stored)",
                entities.size(), recordDate, byBusinessKey.size());
        return entities.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NbuMonthlyInflation> getRecordsForView(LocalDate recordDate, String regionScope) {
        boolean includeRegional = SCOPE_ALL.equalsIgnoreCase(regionScope);

        return inflationRepository.findByRecordDate(recordDate).stream()
                .filter(r -> includeRegional || isNational(r))
                .sorted(Comparator
                        .comparing(NbuMonthlyInflation::getCommodityGroup,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(NbuMonthlyInflation::getIndexType,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRecordDate(LocalDate recordDate) {
        return inflationRepository.countByRecordDate(recordDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NbuMonthlyInflation> findHeadlineCpi(LocalDate recordDate) {
        return inflationRepository.findByRecordDate(recordDate).stream()
                .filter(NbuMonthlyInflationServiceImpl::isNational)
                // pin the CPI family: core inflation and PPI rows share the same
                // index type and group, so filtering only by those is ambiguous
                .filter(r -> "prices_price_cpi_".equalsIgnoreCase(r.getIdApi()))
                .filter(r -> "PCPM_".equalsIgnoreCase(r.getIndexType())
                        && "Total".equalsIgnoreCase(r.getCommodityGroup()))
                .findFirst();
    }

    private static boolean isNational(NbuMonthlyInflation record) {
        return record.getRegionCode() == null || record.getRegionCode().isBlank();
    }

    private static String businessKey(NbuMonthlyInflation entity) {
        // Objects.toString maps a legacy NULL region_code and the normalized ""
        // to the same key, so old rows are still found and updated in place.
        // id_api is part of the key: CPI, core inflation and PPI share the same
        // index_type/commodity_group/region values and must not overwrite each other.
        return entity.getRecordDate() + "|"
                + entity.getIndexType() + "|"
                + entity.getCommodityGroup() + "|"
                + Objects.toString(entity.getRegionCode(), "") + "|"
                + entity.getIdApi();
    }
}

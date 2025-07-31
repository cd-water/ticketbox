package com.cdwater.ticketbox.performance.domain;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SectionService {

    private final PerformanceRepository performances;
    private final PriceTierRepository priceTiers;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public SectionService(PerformanceRepository performances,
                          PriceTierRepository priceTiers,
                          SectionRepository sections,
                          SeatRepository seats) {
        this.performances = performances;
        this.priceTiers = priceTiers;
        this.sections = sections;
        this.seats = seats;
    }

    @Transactional
    public SectionSummary generate(Long performanceId, SectionCommand command) {
        if (!performances.existsById(performanceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
        }
        PriceTier tier = priceTiers.findById(command.priceTierId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRICE_TIER_NOT_FOUND", "Price tier not found"));
        if (!tier.getPerformanceId().equals(performanceId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_TIER_MISMATCH",
                    "Price tier does not belong to this performance");
        }
        if (sections.existsByPerformanceIdAndName(performanceId, command.name())) {
            throw sectionNameTaken();
        }

        Section section;
        try {
            section = sections.saveAndFlush(new Section(
                    performanceId, command.name(), tier.getId(), command.rowCount(), command.seatsPerRow()));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("uk_section_performance_name")) {
                throw sectionNameTaken();
            }
            throw exception;
        }

        List<Seat> generated = new ArrayList<>(command.rowCount() * command.seatsPerRow());
        for (int row = 1; row <= command.rowCount(); row++) {
            for (int number = 1; number <= command.seatsPerRow(); number++) {
                generated.add(new Seat(performanceId, section.getId(), row, number));
            }
        }
        seats.saveAll(generated);

        return new SectionSummary(
                section.getId(),
                section.getName(),
                PriceTierView.from(tier),
                section.getRowCount(),
                section.getSeatsPerRow(),
                generated.size());
    }

    @Transactional(readOnly = true)
    public List<SectionSummary> listSections(Long performanceId) {
        if (!performances.existsById(performanceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
        }
        Map<Long, PriceTierView> tierById = priceTiers.findByPerformanceIdOrderByPriceAsc(performanceId).stream()
                .collect(Collectors.toMap(PriceTier::getId, PriceTierView::from));
        return sections.findByPerformanceIdOrderByIdAsc(performanceId).stream()
                .map(section -> new SectionSummary(
                        section.getId(),
                        section.getName(),
                        tierById.get(section.getPriceTierId()),
                        section.getRowCount(),
                        section.getSeatsPerRow(),
                        section.getRowCount() * section.getSeatsPerRow()))
                .toList();
    }

    private static ApiException sectionNameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SECTION_NAME_TAKEN",
                "A section with this name already exists for the performance");
    }
}

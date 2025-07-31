package com.cdwater.ticketbox.performance.web;

import com.cdwater.ticketbox.performance.domain.SectionCommand;
import com.cdwater.ticketbox.performance.domain.SectionService;
import com.cdwater.ticketbox.performance.domain.SectionSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/performances")
public class AdminSectionController {

    private final SectionService sectionService;

    public AdminSectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionSummary generate(@PathVariable Long id, @Valid @RequestBody SectionCommand command) {
        return sectionService.generate(id, command.normalized());
    }

    @GetMapping("/{id}/sections")
    public List<SectionSummary> list(@PathVariable Long id) {
        return sectionService.listSections(id);
    }
}

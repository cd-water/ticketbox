package com.cdwater.ticketbox.performance.web;

import com.cdwater.ticketbox.performance.domain.PriceTierCommand;
import com.cdwater.ticketbox.performance.domain.PriceTierService;
import com.cdwater.ticketbox.performance.domain.PriceTierUpdateCommand;
import com.cdwater.ticketbox.performance.domain.PriceTierView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/price-tiers")
public class AdminPriceTierController {

    private final PriceTierService priceTierService;

    public AdminPriceTierController(PriceTierService priceTierService) {
        this.priceTierService = priceTierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceTierView create(@Valid @RequestBody PriceTierCommand command) {
        return PriceTierView.from(priceTierService.create(command.normalized()));
    }

    @PutMapping("/{id}")
    public PriceTierView update(@PathVariable Long id, @Valid @RequestBody PriceTierUpdateCommand command) {
        return PriceTierView.from(priceTierService.update(id, command.normalized()));
    }
}

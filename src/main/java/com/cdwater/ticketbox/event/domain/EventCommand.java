package com.cdwater.ticketbox.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventCommand(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotBlank @Size(max = 64) String city,
        @Size(max = 255) String coverObjectKey) {

    public EventCommand normalized() {
        return new EventCommand(title.trim(), description, city.trim(), coverObjectKey);
    }
}

package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.math.BigDecimal;
import java.time.Instant;

public record SearchCriteria(
        String name,
        String artist,
        Category category,
        Instant dateFrom,
        Instant dateTo,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String location
) {
    public static SearchCriteria empty() {
        return new SearchCriteria(null, null, null, null, null, null, null, null);
    }
}

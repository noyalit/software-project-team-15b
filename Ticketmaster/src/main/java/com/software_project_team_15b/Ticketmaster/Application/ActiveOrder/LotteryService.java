package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.Queue.LotteryEligibilityResult;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Minimal lottery service interface used by purchasing flows.
 * Implementations may live elsewhere; this exists so tests can mock it.
 */
@Service
public class LotteryService {
    public LotteryEligibilityResult getLotteryEligibilityForEvent(UUID userId, UUID eventId) {
        throw new UnsupportedOperationException("This method should be mocked in tests");
    }
}

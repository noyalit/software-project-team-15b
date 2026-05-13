package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;

public interface ILotteryDomainService {
    LotteryEligibilityResult getLotteryEligibilityForEvent(UUID userId, UUID eventId);
}

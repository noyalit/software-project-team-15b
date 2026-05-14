package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;

public interface ILotteryDomainService {
    LotteryEligibilityDTO getLotteryEligibilityForEvent(UUID userId, UUID eventId);
}

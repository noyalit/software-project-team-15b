package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryEligibilityResult;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class LotteryDomainServiceImpl implements ILotteryDomainService {

    private final LotteryService lotteryService;

    public LotteryDomainServiceImpl(LotteryService lotteryService) {
        this.lotteryService = Objects.requireNonNull(lotteryService);
    }

    @Override
    public LotteryEligibilityResult getLotteryEligibilityForEvent(UUID userId, UUID eventId) {
        return lotteryService.getLotteryEligibilityForEvent(userId, eventId);
    }
}
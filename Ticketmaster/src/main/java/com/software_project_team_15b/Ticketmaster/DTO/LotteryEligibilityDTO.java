package com.software_project_team_15b.Ticketmaster.DTO;

public record LotteryEligibilityDTO(
        LotteryEligibilityStatus status
) {
    public boolean canCreateActiveOrder() {
        return status == LotteryEligibilityStatus.NO_LOTTERY_REQUIRED
                || status == LotteryEligibilityStatus.WON_AND_ACCESS_VALID;
    }
}

package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.UUID;

public interface ILotteryRepository {
    public void addLottery(Lottery lottery);
    public void removeLottery(Lottery lottery);
    public Lottery getLottery(UUID eventId);
    public void updateLottery(Lottery lottery);
}

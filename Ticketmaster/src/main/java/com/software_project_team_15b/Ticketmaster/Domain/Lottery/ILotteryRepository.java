package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

public interface ILotteryRepository<T> {
    public void addLottery(Lottery<T> lottery);
    public void removeLottery(Lottery<T> lottery);
    public Lottery<T> getLottery(Lottery<T> lottery);
    public void updateLottery(Lottery<T> lottery);
}

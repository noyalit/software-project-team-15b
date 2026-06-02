package com.software_project_team_15b.Ticketmaster.Infrastructure.Lottery;

import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaLotterySpringDataRepository extends JpaRepository<Lottery, UUID> {
}

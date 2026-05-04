package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import java.util.UUID;

public interface IQueueRepository {
    public void addQueue(VirtualQueue queue);
    public void removeQueue(VirtualQueue queue);
    public VirtualQueue getQueue(UUID queueId);
    public void updateQueue(VirtualQueue queue);
}

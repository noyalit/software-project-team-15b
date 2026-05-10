package com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent;

import java.util.UUID;

public interface EventSubscriber {
    void notifyEventIsCancelled(UUID event);
}
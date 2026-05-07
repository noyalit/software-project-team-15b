package com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class EventCancelManager {
    private final List<EventSubscriber> subscribers = new ArrayList<>();

    public void subscribe(EventSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(EventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    public void cancelEvent(UUID event) {
        
        for (EventSubscriber subscriber : subscribers) {
            subscriber.notifyEventIsCancelled(event);
        }
    }
}

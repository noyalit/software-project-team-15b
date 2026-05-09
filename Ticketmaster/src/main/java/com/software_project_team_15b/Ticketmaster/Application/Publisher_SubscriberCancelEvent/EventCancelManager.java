package com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component // Tells Spring to manage this class as a Singleton
public class EventCancelManager {
    private final List<EventSubscriber> subscribers = new CopyOnWriteArrayList<>();

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

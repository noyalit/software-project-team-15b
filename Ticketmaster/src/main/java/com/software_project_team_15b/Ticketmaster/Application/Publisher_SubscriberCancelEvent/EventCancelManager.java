package com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component // Tells Spring to manage this class as a Singleton
public class EventCancelManager {
    private final List<EventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(EventSubscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("Subscriber cannot be null");
        }
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber);
        }
    }

    public void unsubscribe(EventSubscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("Subscriber cannot be null");
        }
        subscribers.remove(subscriber);
    }

    public void cancelEvent(UUID event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        Exception lastException = null;
        for (EventSubscriber subscriber : subscribers) {
            try {
                subscriber.notifyEventIsCancelled(event);
            } catch (Exception e) {
                lastException = e;
            }
        } 
        if (lastException != null) {
            throw new RuntimeException("Failed to notify all subscribers about event cancellation", lastException);
        }   
    }
}

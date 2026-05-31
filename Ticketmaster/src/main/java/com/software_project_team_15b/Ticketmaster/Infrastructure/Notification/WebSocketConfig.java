package com.software_project_team_15b.Ticketmaster.Infrastructure.Notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * Spring configuration that enables STOMP-over-WebSocket messaging.
 *
 * <p>This is the transport that lets the server push notifications to connected
 * clients without waiting for a client request (solving the "server-initiated
 * message" problem that plain request/response HTTP cannot). It registers the
 * connection endpoint and configures the in-memory message broker used to fan out
 * messages to per-user and per-group topics.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer {

    /**
     * Registers the STOMP endpoint clients use to open the WebSocket connection.
     *
     * <p>Exposes {@code /ws} with a SockJS fallback for browsers/proxies that do not
     * support native WebSockets. All origins are allowed to simplify development.</p>
     *
     * @param registry the registry used to declare STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry.addEndpoint("/ws")  //connection entry point for clients
                .setAllowedOriginPatterns("*")  // Allows all origins for development; consider restricting later
                .withSockJS();
    }

    /**
     * Configures the message broker and destination prefixes.
     *
     * <p>Enables a simple in-memory broker for destinations prefixed with
     * {@code /topic} (the channels notifications are published to) and routes
     * client-bound application messages through the {@code /app} prefix.</p>
     *
     * @param registry the registry used to configure the broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        registry.enableSimpleBroker("/topic");

        registry.setApplicationDestinationPrefixes("/app");
    }

}

package com.software_project_team_15b.Ticketmaster.Application.Notification;

import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

import java.util.UUID;

public interface INotifier {

        void notifyUser(UUID userId, NotificationDTO notification);
    
        void notifyCompanyManagers(UUID companyId, NotificationDTO notification);
    
        void notifyEventAttendees(UUID eventId, NotificationDTO notification);
    
}

package com.software_project_team_15b.Ticketmaster.Application.Notification;

import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

public interface INotifier {

        void notifyUser(String userId, NotificationDTO notification);
    
        void notifyCompanyManagers(String companyId, NotificationDTO notification);
    
        void notifyEventAttendees(String eventId, NotificationDTO notification);
    
}

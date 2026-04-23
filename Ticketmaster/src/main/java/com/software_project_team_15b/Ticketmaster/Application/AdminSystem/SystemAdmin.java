package com.software_project_team_15b.Ticketmaster.Application.AdminSystem;

import java.util.List;

public class SystemAdmin {

    public void closeProductionCompany(String companyId, String reason) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void removeSubscriberFromPlatform(String subscriberId, String reason) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<?> listComplaints(Object query) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void respondToComplaint(String complaintId, String responseMessage) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void sendSystemMessageToProducers(List<String> producerIds, String message) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void sendSystemMessageToPurchasers(List<String> purchaserIds, String message) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<?> getGlobalPurchaseHistoryByBuyer(Object query) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<?> getGlobalPurchaseHistoryByCompanyOrEvent(Object query) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Object getAnalytics(Object query) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<?> listActiveQueues() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void setQueueFlowRate(String queueId, int permitsPerSecond) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void clearQueue(String queueId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}

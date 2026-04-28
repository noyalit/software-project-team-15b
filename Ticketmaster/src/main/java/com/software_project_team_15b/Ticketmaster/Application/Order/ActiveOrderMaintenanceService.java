package com.software_project_team_15b.Ticketmaster.Application.Order;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActiveOrderMaintenanceService {

    private static final Logger AUDIT =
            LoggerFactory.getLogger("audit.active-order-maintenance");

    private final IActiveOrderRepository activeOrderRepository;
    private final EventManagementService eventManagementService;

    public ActiveOrderMaintenanceService(
            IActiveOrderRepository activeOrderRepository,
            EventManagementService eventManagementService
    ) {
        if (activeOrderRepository == null || eventManagementService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }

        this.activeOrderRepository = activeOrderRepository;
        this.eventManagementService = eventManagementService;
    }

    @Transactional
    @Scheduled(cron = "${active-orders.cleanup-non-active-cron:0 0 3 * * *}")
    public void deleteNonActiveOrders() {
        List<ActiveOrder> ordersToDelete =
                activeOrderRepository.findByStatusNot(ActiveOrderStatus.ACTIVE);

        if (ordersToDelete.isEmpty()) {
            return;
        }

        activeOrderRepository.deleteAll(ordersToDelete);

        AUDIT.info("op=deleteNonActiveOrders count={} result=ok",
                ordersToDelete.size());
    }

    @Transactional
    @Scheduled(fixedDelayString = "${active-orders.expired-active-scan-ms:300000}")
    public void releaseAndDeleteExpiredActiveOrders() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(1);

        List<ActiveOrder> expiredActiveOrders =
                activeOrderRepository.findByStatusAndExpiresAtBefore(
                        ActiveOrderStatus.ACTIVE,
                        expiredBefore
                );

        if (expiredActiveOrders.isEmpty()) {
            return;
        }

        for (ActiveOrder activeOrder : expiredActiveOrders) {
            releaseAndDeleteExpiredActiveOrder(activeOrder);
        }

        AUDIT.info("op=releaseAndDeleteExpiredActiveOrders count={} expiredBefore={} result=ok",
                expiredActiveOrders.size(),
                expiredBefore);
    }

    private void releaseAndDeleteExpiredActiveOrder(ActiveOrder activeOrder) {
        activeOrderRepository.delete(activeOrder);

        if (activeOrder.getOrderSeats().isEmpty()) {
            // No seats to release, skip event management call
            AUDIT.info("op=releaseAndDeleteExpiredActiveOrder order={} event={} result=no-seats-to-release",
                    activeOrder.getOrderId(),
                    activeOrder.getEventId());
            return;
        }
        
        eventManagementService.release(
                activeOrder.getEventId(),
                activeOrder.getOrderId()
        );

        AUDIT.info("op=releaseAndDeleteExpiredActiveOrder order={} event={} result=ok",
                activeOrder.getOrderId(),
                activeOrder.getEventId());
    }
}
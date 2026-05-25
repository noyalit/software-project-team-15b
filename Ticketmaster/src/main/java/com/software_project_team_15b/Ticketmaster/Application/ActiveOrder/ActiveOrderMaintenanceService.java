package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;

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
    private final IEventDomainService eventDomainService;

    public ActiveOrderMaintenanceService(
            IActiveOrderRepository activeOrderRepository,
            IEventDomainService eventDomainService
    ) {
        if (activeOrderRepository == null || eventDomainService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }

        this.activeOrderRepository = activeOrderRepository;
        this.eventDomainService = eventDomainService;
    }

    @Transactional
    @Scheduled(cron = "${active-orders.cleanup-non-active-cron:0 0 3 * * *}")
    public void deleteNonActiveOrders() {
        try {
            List<ActiveOrder> ordersToDelete =
                    activeOrderRepository.findByStatusNotForUpdate(ActiveOrderStatus.ACTIVE);

            if (ordersToDelete.isEmpty()) {
                return;
            }

            activeOrderRepository.deleteAll(ordersToDelete);

            AUDIT.info("op=deleteNonActiveOrders count={} result=ok",
                    ordersToDelete.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=deleteNonActiveOrders result=error reason={}", e.getMessage(), e);
        }
    }

    @Transactional
    @Scheduled(fixedDelayString = "${active-orders.expired-active-scan-ms:300000}")
    public void releaseAndDeleteExpiredActiveOrders() {
        try {
            LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(1);

            List<ActiveOrder> expiredActiveOrders =
                    activeOrderRepository.findExpiredActiveOrdersForUpdate(
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
        } catch (RuntimeException e) {
            AUDIT.warn("op=releaseAndDeleteExpiredActiveOrders result=error reason={}", e.getMessage(), e);
        }
    }

    private void releaseAndDeleteExpiredActiveOrder(ActiveOrder activeOrder) {
        try {
            if (activeOrder.getOrderSeats().isEmpty()) {
            activeOrderRepository.delete(activeOrder);
            // No seats to release, skip event management call
            AUDIT.info("op=releaseAndDeleteExpiredActiveOrder order={} event={} result=no-seats-to-release",
                activeOrder.getOrderId(),
                activeOrder.getEventId());
            return;
            }

            eventDomainService.release(
                activeOrder.getEventId(),
                activeOrder.getOrderId()
            );

            activeOrderRepository.delete(activeOrder);

            AUDIT.info("op=releaseAndDeleteExpiredActiveOrder order={} event={} result=ok",
                activeOrder.getOrderId(),
                activeOrder.getEventId());
        } catch (RuntimeException e) {
            AUDIT.warn("op=releaseAndDeleteExpiredActiveOrder order={} event={} result=error reason={}",
                activeOrder != null ? activeOrder.getOrderId() : null,
                activeOrder != null ? activeOrder.getEventId() : null,
                e.getMessage(), e);
        }
    }
}
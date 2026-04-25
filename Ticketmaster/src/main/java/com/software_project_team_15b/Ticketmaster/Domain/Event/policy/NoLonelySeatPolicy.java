package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompPurchasePolicy;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class NoLonelySeatPolicy implements IEventPurchasePolicy {

    @JsonCreator
    public NoLonelySeatPolicy() {}

    @Override
    public void validate(PurchaseRequest request, Event event, ICompPurchasePolicy companyPolicy) {
        if (companyPolicy != null) companyPolicy.validate(request);
        if (request.seatIds() == null || request.seatIds().isEmpty()) return;

        EventArea area = event.areas().stream()
                .filter(a -> a.areaId().equals(request.areaId()))
                .findFirst()
                .orElse(null);
        if (!(area instanceof SeatingEventArea seating)) return;

        Set<UUID> proposed = new HashSet<>(request.seatIds());

        TreeMap<String, TreeMap<Integer, Seat>> byRow = new TreeMap<>();
        for (Seat s : seating.seats().values()) {
            byRow.computeIfAbsent(s.row(), r -> new TreeMap<>(Comparator.naturalOrder()))
                    .put(parseSeatNumber(s.number()), s);
        }

        for (var rowEntry : byRow.entrySet()) {
            TreeMap<Integer, Seat> row = rowEntry.getValue();
            List<Integer> cols = row.keySet().stream().sorted().toList();
            for (int i = 0; i < cols.size(); i++) {
                int col = cols.get(i);
                Seat s = row.get(col);
                if (proposed.contains(s.seatId())) continue;
                if (isOccupiedOrProposed(s, proposed)) continue;
                Integer leftCol = i > 0 ? cols.get(i - 1) : null;
                Integer rightCol = i < cols.size() - 1 ? cols.get(i + 1) : null;
                boolean leftBlocked = leftCol != null && isOccupiedOrProposed(row.get(leftCol), proposed);
                boolean rightBlocked = rightCol != null && isOccupiedOrProposed(row.get(rightCol), proposed);
                if (leftBlocked && rightBlocked) {
                    throw new PolicyViolationException(
                            "hold would leave a lonely seat at row " + s.row() + " number " + s.number());
                }
            }
        }
    }

    private boolean isOccupiedOrProposed(Seat s, Set<UUID> proposed) {
        if (proposed.contains(s.seatId())) return true;
        if (s.status() == SeatStatus.SOLD) return true;
        if (s.status() == SeatStatus.HELD) return true;
        return false;
    }

    private int parseSeatNumber(String n) {
        try {
            return Integer.parseInt(n.trim());
        } catch (NumberFormatException e) {
            return n.hashCode();
        }
    }
}

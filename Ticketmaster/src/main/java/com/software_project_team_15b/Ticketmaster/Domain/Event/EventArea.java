package com.software_project_team_15b.Ticketmaster.Domain.Event;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import java.util.Objects;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "area_type")
public abstract class EventArea {

    @Id
    protected UUID areaId;

    @Column(nullable = false)
    protected String name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "base_price_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "base_price_currency", nullable = false))
    })
    protected Money basePrice;

    protected EventArea() {}

    protected EventArea(UUID areaId, String name, Money basePrice) {
        if (areaId == null) throw new IllegalArgumentException("areaId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be null or blank");
        Objects.requireNonNull(basePrice, "basePrice must not be null");
        this.areaId = areaId;
        this.name = name;
        this.basePrice = basePrice;
    }

    public UUID areaId() { return areaId; }
    public String name() { return name; }
    public Money basePrice() { return basePrice; }

    public abstract int availableCapacity();
    public abstract boolean releaseByToken(UUID token);
    public abstract void confirmByToken(UUID token);
    public abstract boolean hasActiveHolds();
}

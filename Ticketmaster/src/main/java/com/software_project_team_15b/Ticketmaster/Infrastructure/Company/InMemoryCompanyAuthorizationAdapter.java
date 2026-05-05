package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyAuthorizationAdapter implements ICompanyAuthorizationPort {

    @Override
    public boolean canManageEvent(UUID companyId, UUID callerId) {
        return true;
    }
}

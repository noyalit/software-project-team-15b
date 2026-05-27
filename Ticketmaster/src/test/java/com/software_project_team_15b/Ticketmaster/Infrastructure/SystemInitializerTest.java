package com.software_project_team_15b.Ticketmaster.Infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.software_project_team_15b.Ticketmaster.Application.IPasswordEncoder;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@ExtendWith(MockitoExtension.class)
class SystemInitializerTest {

    @Mock private ISystemAdminRepository systemAdminRepository;
    @Mock private IPasswordEncoder passwordEncoder;

    @Test
    void run_creates_default_admin_when_missing() {
        when(systemAdminRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin123")).thenReturn("encoded");

        SystemInitializer initializer = new SystemInitializer(
                systemAdminRepository,
                passwordEncoder,
                "admin",
                "Admin123"
        );

        initializer.run(null);

        ArgumentCaptor<SystemAdmin> captor = ArgumentCaptor.forClass(SystemAdmin.class);
        verify(systemAdminRepository).save(captor.capture());

        SystemAdmin created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("admin");
        assertThat(created.getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void run_does_nothing_when_default_admin_already_exists() {
        when(systemAdminRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new SystemAdmin("admin", "SomeHashValue")));

        SystemInitializer initializer = new SystemInitializer(
                systemAdminRepository,
                passwordEncoder,
                "admin",
                "Admin123"
        );

        initializer.run(null);

        verify(systemAdminRepository, never()).save(any(SystemAdmin.class));
    }
}

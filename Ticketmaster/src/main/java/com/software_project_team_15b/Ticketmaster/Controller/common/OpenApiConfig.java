package com.software_project_team_15b.Ticketmaster.Controller.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "IAuth";

    @Bean
    public OpenAPI ticketmasterOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketmaster API")
                        .description("REST API for the Ticketmaster API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}

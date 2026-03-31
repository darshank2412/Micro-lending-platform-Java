package com.darshan.lending.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Micro Lending Platform API",
                version     = "v1",
                description = "Backend APIs for Micro Lending Platform"
        ),
        servers = @Server(url = "http://localhost:8081", description = "Local Dev"),
        security = @SecurityRequirement(name = "bearerAuth")  // ← fixed
)
@SecuritySchemes({
        @SecurityScheme(
                name   = "basicAuth",
                type   = SecuritySchemeType.HTTP,
                scheme = "basic",
                in     = SecuritySchemeIn.HEADER,
                description = "Use phone number as username. Admin: 9999999999 / admin123"
        ),
        @SecurityScheme(
                name         = "bearerAuth",
                type         = SecuritySchemeType.HTTP,
                scheme       = "bearer",
                bearerFormat = "JWT",
                in           = SecuritySchemeIn.HEADER
        )
})
public class OpenApiConfig {
}
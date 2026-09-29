package com.neoul.ex.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";
    public static final String SHIP_API_KEY = "shipApiKey";

    @Bean
    public OpenAPI neoulOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Neoul API").version("v1")
                        .description("빛가람 API 문서이며 일반 업무 응답은 success status code message data를 포함합니다"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("로그인 응답의 accessToken 값만 입력합니다"))
                        .addSecuritySchemes(SHIP_API_KEY, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-API-Key")
                                .description("해당 무인배에 발급된 API 키를 입력합니다")));
    }
}

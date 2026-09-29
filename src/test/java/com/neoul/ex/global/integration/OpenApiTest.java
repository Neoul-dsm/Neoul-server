package com.neoul.ex.global.integration;

import com.neoul.ex.domain.auth.dto.SignupRequest;
import com.neoul.ex.domain.auth.dto.SignupResponse;
import com.neoul.ex.domain.ship.dto.ShipConnectionResponse;
import com.neoul.ex.domain.ship.dto.ShipLocationResponse;
import com.neoul.ex.domain.ship.dto.ShipResponse;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired private MockMvc mvc;

    @Test
    void swaggerUiAndItsConfigurationArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @Test
    void documentIncludesApiContractsAndOnlyProtectsAuthenticatedOperations() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Neoul API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.shipApiKey.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.shipApiKey.name").value("X-API-Key"))
                .andExpect(jsonPath("$.paths['/ships/{shipId}/status'].post.security[0].shipApiKey").isArray())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/api-key'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/api-key'].delete.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/beaches'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.required").value(hasItem("name")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.name.maxLength").value(50))
                .andExpect(jsonPath("$.components.schemas.SignupResponse.properties.name.type").value("string"))
                .andExpect(jsonPath("$.paths['/auth/logout'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/beaches/{beachId}/monitoring']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BeachMonitoringResponse").doesNotExist())
                .andExpect(jsonPath("$.paths['/ships'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/connection'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/location'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/solar-power']").doesNotExist())
                .andExpect(jsonPath("$.paths['/ships/{shipId}/battery']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ShipResponse.properties", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.components.schemas.ShipLocationResponse.properties", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.components.schemas.ShipConnectionResponse.properties", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.components.schemas.ShipSolarPowerResponse").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ShipBatteryResponse").doesNotExist())
                .andExpect(jsonPath("$.paths['/alerts/drowning/stream'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/alerts/marineanimal/stream'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/ships'].get.parameters[*].name").value(not(hasItem("principal"))))
                .andExpect(jsonPath("$.components.schemas.MessageLoginResponse.properties", hasKey("success")))
                .andExpect(jsonPath("$.components.schemas.MessageLoginResponse.properties", hasKey("status")))
                .andExpect(jsonPath("$.components.schemas.MessageLoginResponse.properties", hasKey("code")))
                .andExpect(jsonPath("$.components.schemas.MessageLoginResponse.properties", hasKey("message")))
                .andExpect(jsonPath("$.components.schemas.MessageLoginResponse.properties", hasKey("data")));
    }
}

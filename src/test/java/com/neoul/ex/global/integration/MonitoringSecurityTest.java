package com.neoul.ex.global.integration;

import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MonitoringSecurityTest {
    @Autowired private MockMvc mvc;

    @Test
    void shipReadsStillRequireAuthenticationOutsidePreview() throws Exception {
        for (String path : new String[]{"/ships", "/ships/1", "/ships/1/location"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden())
                    .andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }
}

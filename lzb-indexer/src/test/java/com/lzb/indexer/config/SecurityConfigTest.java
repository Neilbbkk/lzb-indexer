package com.lzb.indexer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,prometheus")
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired private MockMvc mvc;

    @Test
    void healthIsOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void prometheusRequiresAuth() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusAllowsConfiguredUser() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());
    }
}

package com.neoul.ex.global.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

public final class CommonResponseAssertions {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CommonResponseAssertions() {
    }

    public static void assertCommonResponse(MvcResult result) {
        var response = result.getResponse();
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
        var body = MAPPER.readTree(response.getContentAsByteArray());
        assertEquals(5, body.size());
        assertTrue(body.get("success").isBoolean());
        assertEquals(response.getStatus() < 400, body.get("success").booleanValue());
        assertTrue(body.get("status").isIntegralNumber());
        assertEquals(response.getStatus(), body.get("status").intValue());
        assertTrue(body.get("code").isString());
        assertTrue(body.get("message").isString());
        assertTrue(body.has("data"), "data must be present even when null");
    }
}
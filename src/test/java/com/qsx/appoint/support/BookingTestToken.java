package com.qsx.appoint.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP fixtures obtain a server-issued token through the same entry point as the browser. */
public final class BookingTestToken {
    private BookingTestToken() {}

    public static String issueBookingToken(MockMvc mvc, String authorization) throws Exception {
        return issueBookingToken(mvc, request -> {
            request.addHeader("Authorization", authorization);
            return request;
        });
    }

    public static String issueBookingToken(MockMvc mvc, RequestPostProcessor authentication) throws Exception {
        var response = mvc.perform(post("/api/v1/appointments/token").with(authentication))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new ObjectMapper().readTree(response.getContentAsString()).at("/data/token").asText();
    }
}

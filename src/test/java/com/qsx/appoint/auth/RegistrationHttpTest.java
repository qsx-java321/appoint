package com.qsx.appoint.auth;

import com.qsx.appoint.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@RegressionTest @AutoConfigureMockMvc @Transactional
class RegistrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired RegistrationTestSupport support;
    @BeforeEach void reset() { support.clear(); }
    @Test void wrongAndExpiredCode() throws Exception {
        String email = "code-test@example.com";
        mvc.perform(post("/api/v1/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\"}")).andExpect(status().isOk());
        String wrong = support.code(email).equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/v1/auth/register/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"code\":\""+wrong+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(3003));
        support.expireCode(email);
        mvc.perform(post("/api/v1/auth/register/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(3004));
    }
    @Test void invalidAndConsumedTicket() throws Exception {
        String body = "{\"email\":\"ticket-test@example.com\",\"password\":\"password123\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body.substring(0,body.length()-1)+",\"signupTicket\":\"unknown\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(3005));
        String verified = RegistrationTestSupport.verifiedBody(mvc, body);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(verified))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(verified))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(3005));
    }
}

package com.qsx.appoint.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.RegressionTest;
import com.qsx.appoint.user.avatar.AvatarObject;
import com.qsx.appoint.user.avatar.AvatarStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 头像上传、公开读取、缓存协商和删除的 HTTP 契约。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:user-avatar;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@Import(UserAvatarHttpTest.FakeStorageConfig.class)
@Transactional
class UserAvatarHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired FakeAvatarStorage storage;

    long userId = 86001L;
    String token;

    @BeforeEach
    void setUp() {
        storage.clear();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,'unused')",
                userId, "avatar@example.com");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='USER'", userId);
        token = "Bearer " + tokens.createToken(userId, "avatar@example.com", List.of("USER"));
    }

    @Test
    void uploadReturnsAvatarUrlAndPublicGetServesImageWithCacheHeaders() throws Exception {
        byte[] image = png();
        MvcResult uploaded = mvc.perform(upload(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.startsWith(
                        "/api/v1/users/" + userId + "/avatar?v=")))
                .andReturn();
        String avatarUrl = json.readTree(uploaded.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        assertThat(storage.size()).isEqualTo(1);

        MvcResult served = mvc.perform(get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(image);
        String etag = served.getResponse().getHeader("ETag");
        assertThat(etag).startsWith("\"").endsWith(".png\"");

        mvc.perform(get(avatarUrl).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void uploadRequiresLoginAndRejectsInvalidOrMissingFile() throws Exception {
        mvc.perform(upload(png(), null))
                .andExpect(status().isUnauthorized());

        mvc.perform(upload("hello".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3101));

        MockMultipartHttpServletRequestBuilder missing = multipart("/api/v1/users/me/avatar");
        missing.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });
        mvc.perform(missing.header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        assertThat(storage.size()).isZero();
    }

    @Test
    void deleteRemovesObjectAndIsIdempotent() throws Exception {
        String avatarUrl = uploadAndGetUrl(png());
        mvc.perform(get(avatarUrl)).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.nullValue()));

        assertThat(storage.size()).isZero();
        mvc.perform(get(avatarUrl)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
        mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void meIncludesAvatarUrlAndMissingAvatarIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.nullValue()));

        uploadAndGetUrl(png());

        mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.startsWith(
                        "/api/v1/users/" + userId + "/avatar?v=")));
        mvc.perform(get("/api/v1/users/999999/avatar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void oversizedImageIsRejectedBeforeStorage() throws Exception {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;

        mvc.perform(upload(oversized))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3101));

        assertThat(storage.size()).isZero();
    }

    private MockMultipartHttpServletRequestBuilder upload(byte[] image) {
        return upload(image, token);
    }

    private MockMultipartHttpServletRequestBuilder upload(byte[] image, String authorization) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/users/me/avatar");
        request.with(req -> {
            req.setMethod(HttpMethod.PUT.name());
            return req;
        });
        request.file(new MockMultipartFile("file", "avatar.png", "image/png", image));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return request;
    }

    private String uploadAndGetUrl(byte[] image) throws Exception {
        MvcResult result = mvc.perform(upload(image))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("avatarUrl").asText();
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStorageConfig {
        @Bean
        @Primary
        FakeAvatarStorage fakeAvatarStorage() {
            return new FakeAvatarStorage();
        }
    }

    static class FakeAvatarStorage implements AvatarStorage {
        private final Map<String, AvatarObject> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, new AvatarObject(content, contentType));
        }

        @Override
        public Optional<AvatarObject> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void delete(String key) {
            if (key != null) {
                objects.remove(key);
            }
        }

        void clear() {
            objects.clear();
        }

        int size() {
            return objects.size();
        }
    }
}

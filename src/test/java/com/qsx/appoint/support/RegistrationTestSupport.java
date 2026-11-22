package com.qsx.appoint.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

/** 测试专用 Redis/SMTP 边界，不访问任何常驻资源。 */
@TestConfiguration(proxyBeanMethods = false)
public class RegistrationTestSupport {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> codes = new ConcurrentHashMap<>();
    @Bean @Primary
    @SuppressWarnings("unchecked")
    StringRedisTemplate registrationRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(ops.getAndDelete(anyString())).thenAnswer(call -> values.remove(call.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(call -> values.remove(call.getArgument(0)) != null);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList(),
                anyString(), anyString(), anyString(), anyString())).thenAnswer(call ->
                claimBookingToken(call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
        return redis;
    }

    // Regression tests keep Redis in memory. The Lua implementation runs against real Redis in mysql-it.
    private synchronized String claimBookingToken(List<String> keys, String userId, String hash, String appointmentNo) {
        String binding = values.get(keys.get(1));
        if (binding != null) {
            String[] fields = binding.split(":", 3);
            if (!fields[0].equals(userId)) return "INVALID";
            return fields[1].equals(hash) ? "PENDING" : "CONFLICT";
        }
        if (!userId.equals(values.get(keys.getFirst()))) return "INVALID";
        values.put(keys.get(1), userId + ":" + hash + ":" + appointmentNo);
        values.remove(keys.getFirst());
        return "CLAIMED";
    }
    @Bean @Primary
    JavaMailSender registrationSmtp() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doAnswer(call -> {
            SimpleMailMessage message = call.getArgument(0);
            var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(message.getText());
            if (!matcher.find()) throw new AssertionError("验证码邮件无六位数字");
            codes.put(message.getTo()[0], matcher.group()); return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        return sender;
    }
    public String code(String email) { return codes.get(email); }
    public void expireCode(String email) { values.keySet().removeIf(key -> key.endsWith(":register-code:" + email)); }
    public void clear() { values.clear(); codes.clear(); }

    public static String verifiedBody(MockMvc mvc, String body) throws Exception {
        var json = new ObjectMapper();
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body);
        String email = request.get("email").asText();
        mvc.perform(post("/api/v1/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email)))).andExpect(status().isOk());
        // 从测试 SMTP 捕获邮件，仍完整经过发送及验证接口。
        var context = mvc.getDispatcherServlet().getWebApplicationContext();
        var support = context.getBean(RegistrationTestSupport.class);
        String result = mvc.perform(post("/api/v1/auth/register/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "code", support.code(email)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        request.put("signupTicket", json.readTree(result).at("/data/signupTicket").asText());
        return json.writeValueAsString(request);
    }
}

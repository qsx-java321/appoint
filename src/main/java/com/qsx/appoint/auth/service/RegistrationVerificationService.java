package com.qsx.appoint.auth.service;

import com.qsx.appoint.auth.config.RegistrationProperties;
import com.qsx.appoint.auth.dto.RegistrationCodeResponse;
import com.qsx.appoint.auth.dto.SignupTicketResponse;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.service.UserService;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 本地注册的邮箱验证凭证；只有最终注册才创建用户。 */
@Service
@RequiredArgsConstructor
public class RegistrationVerificationService {
    private final StringRedisTemplate redis;
    private final RegistrationMailSender mail;
    private final RegistrationProperties properties;
    private final UserService users;
    private final SecureRandom random = new SecureRandom();

    public RegistrationCodeResponse sendCode(String email) {
        email = normalize(email);
        if (users.findByEmail(email) != null) throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(key("register-code", email), code, properties.codeTtl());
        mail.sendCode(email, code);
        return new RegistrationCodeResponse(properties.codeTtl().toSeconds());
    }

    public SignupTicketResponse verifyCode(String email, String code) {
        email = normalize(email);
        String codeKey = key("register-code", email);
        String expected = redis.opsForValue().get(codeKey);
        if (expected == null) throw new BusinessException(ErrorCode.REGISTRATION_CODE_EXPIRED);
        if (!expected.equals(code)) throw new BusinessException(ErrorCode.REGISTRATION_CODE_INVALID);
        redis.delete(codeKey);
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(key("signup-ticket", ticket), email, properties.ticketTtl());
        return new SignupTicketResponse(ticket, properties.ticketTtl().toSeconds());
    }

    public void consumeTicket(String email, String ticket) {
        String verifiedEmail = redis.opsForValue().getAndDelete(key("signup-ticket", ticket));
        if (verifiedEmail == null || !verifiedEmail.equals(normalize(email))) {
            throw new BusinessException(ErrorCode.SIGNUP_TICKET_INVALID);
        }
    }

    private String key(String purpose, String value) {
        return "appoint:" + properties.namespace() + ":auth:" + purpose + ":" + value;
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}

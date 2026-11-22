package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.dto.BookingTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Redis 中的下单许可和首次提交所选编号。成功回执由 MySQL 保存。 */
@Component
final class BookingTokenStore {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final RedisScript<String> CLAIM = RedisScript.of("""
            local binding = redis.call('GET', KEYS[2])
            if binding then
                local owner, requestHash = string.match(binding, '^([^:]+):([^:]+):')
                if owner ~= ARGV[1] then return 'INVALID' end
                if requestHash ~= ARGV[2] then return 'CONFLICT' end
                return 'PENDING'
            end
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 'INVALID' end
            redis.call('SET', KEYS[2], ARGV[1] .. ':' .. ARGV[2] .. ':' .. ARGV[3], 'EX', ARGV[4])
            redis.call('DEL', KEYS[1])
            return 'CLAIMED'
            """, String.class);

    enum Claim { CLAIMED, PENDING, CONFLICT, INVALID }

    private final StringRedisTemplate redis;
    private final String prefix;

    BookingTokenStore(StringRedisTemplate redis, @Value("${appoint.local-environment.name:local}") String environment) {
        this.redis = redis;
        this.prefix = "appoint:" + environment + ":booking:";
    }

    BookingTokenResponse issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(tokenKey(token), userId.toString(), TTL);
        return new BookingTokenResponse(token, TTL.toSeconds());
    }

    Claim claim(Long userId, String token, String requestHash, String appointmentNo) {
        String outcome = redis.execute(CLAIM, List.of(tokenKey(token), bindingKey(token)),
                userId.toString(), requestHash, appointmentNo, Long.toString(TTL.toSeconds()));
        return Claim.valueOf(outcome);
    }

    void discardBinding(String token) {
        redis.delete(bindingKey(token));
    }

    private String tokenKey(String token) { return prefix + "token:" + token; }

    private String bindingKey(String token) { return prefix + "binding:" + token; }
}

package com.qsx.appoint.catalog.cache;

import com.qsx.appoint.catalog.dto.HospitalResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 对象层验证缓存错误边界和事务绕过；真实提交/TTL由RedisIT承担。 */
class CatalogDirectoryCacheTest {
    StringRedisTemplate redis;
    ValueOperations<String,String> values;
    SimpleMeterRegistry meters;
    CatalogDirectoryCache cache;
    final String key = "appoint:unit:catalog:v1:hospitals";
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        meters = new SimpleMeterRegistry();
        cache = new CatalogDirectoryCache(redis, props(true), meters);
    }
    CatalogCacheProperties props(boolean enabled) {
        return new CatalogCacheProperties(enabled,"unit",Duration.ofMinutes(5),Duration.ofMinutes(5),Duration.ofMinutes(2),Duration.ofSeconds(30));
    }
    @Test void databaseFailureIsPropagatedOnceAndNeverCached() {
        @SuppressWarnings("unchecked") Supplier<List<HospitalResponse>> loader = mock(Supplier.class);
        RuntimeException failure = new IllegalStateException("db unavailable");
        when(loader.get()).thenThrow(failure);
        assertThatThrownBy(() -> cache.hospitals(loader)).isSameAs(failure);
        verify(loader).get();
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void redisReadFailureLoadsDatabaseWithoutRetryingRedisWrite() {
        when(values.get(key)).thenThrow(new IllegalStateException("offline"));
        assertThat(cache.hospitals(() -> List.of(new HospitalResponse(1L,"医院","地址"))))
                .extracting(HospitalResponse::getName).containsExactly("医院");
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
        assertThat(meters.get("catalog.cache.requests").tag("outcome","read_error").counter().count()).isEqualTo(1);
    }
    @Test void writeFailurePreservesLoadedResult() {
        doThrow(new IllegalStateException("full")).when(values).set(eq(key),anyString(),any(Duration.class));
        assertThat(cache.hospitals(List::of)).isEmpty();
        assertThat(meters.get("catalog.cache.operations").tag("outcome","error").counter().count()).isEqualTo(1);
    }
    @Test void malformedAndIncompleteValuesRecoverThroughDatabase() {
        for (String invalid : List.of("{", "null", "[null]", "[{}]", "{}")) {
            when(values.get(key)).thenReturn(invalid);
            assertThat(cache.hospitals(() -> List.of(new HospitalResponse(2L,"有效","地址"))))
                    .extracting(HospitalResponse::getId).containsExactly(2L);
        }
        assertThat(meters.get("catalog.cache.requests").tag("outcome","decode_error").counter().count()).isEqualTo(5);
    }
    @Test void activeTransactionBypassesRedisAndDoesNotPublishUncommittedValues() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThat(cache.hospitals(List::of)).isEmpty(); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(values);
        assertThat(meters.get("catalog.cache.requests").tag("outcome","bypass_transaction").counter().count()).isEqualTo(1);
    }
    @Test void disabledCacheNeverReadsOrRegistersInvalidation() {
        cache = new CatalogDirectoryCache(redis,props(false),meters);
        assertThat(cache.hospitals(List::of)).isEmpty();
        cache.evictHospitalsAfterCommit();
        verifyNoInteractions(values);
        verify(redis,never()).delete(anyString());
    }
    @Test void enabledInvalidationRequiresWriteTransaction() {
        assertThatThrownBy(cache::evictHospitalsAfterCommit).isInstanceOf(IllegalStateException.class);
        verify(redis,never()).delete(anyString());
    }
    @Test void configurationRejectsUnsafeNamespaceAndInvalidTtlOnlyWhenEnabled() {
        assertThatThrownBy(() -> new CatalogCacheProperties(true,"*",Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogCacheProperties(true,"valid",Duration.ZERO,Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new CatalogCacheProperties(false,null,null,null,null,null)).doesNotThrowAnyException();
    }
}

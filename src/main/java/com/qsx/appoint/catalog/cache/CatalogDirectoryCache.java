package com.qsx.appoint.catalog.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.dto.DepartmentResponse;
import com.qsx.appoint.catalog.dto.DoctorResponse;
import com.qsx.appoint.catalog.dto.HospitalResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/** 只缓存公开目录；数据库加载不在缓存异常捕获范围中，故障不会变成空目录。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogDirectoryCache {
    private final StringRedisTemplate redis;
    private final CatalogCacheProperties properties;
    private final MeterRegistry meters;
    // 缓存格式与 HTTP ObjectMapper 分离，避免 HTTP Long 转文本影响内部往返。
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong lastWarning = new AtomicLong();

    public List<HospitalResponse> hospitals(Supplier<List<HospitalResponse>> loader) {
        return read("hospitals", "hospitals", properties.hospitalTtl(), HospitalRow.class, loader,
                x -> new HospitalRow(x.getId(), x.getName(), x.getAddress()));
    }
    public List<DepartmentResponse> departments(Long hospitalId, Supplier<List<DepartmentResponse>> loader) {
        return read("departments", "departments:" + hospitalId, properties.departmentTtl(), DepartmentRow.class,
                loader, x -> new DepartmentRow(x.getId(), x.getName()));
    }
    public List<DoctorResponse> doctors(Long departmentId, Supplier<List<DoctorResponse>> loader) {
        return read("doctors", "doctors:" + departmentId, properties.doctorTtl(), DoctorRow.class, loader,
                x -> new DoctorRow(x.getId(), x.getDoctorNo(), x.getName(), x.getTitle(), x.getIntroduction()));
    }

    public void evictHospitalsAfterCommit() { afterCommit("hospitals", "hospitals"); }
    public void evictDepartmentsAfterCommit(Long hospitalId) { afterCommit("departments", "departments:" + hospitalId); }
    public void evictDoctorsAfterCommit(Long departmentId) { afterCommit("doctors", "doctors:" + departmentId); }

    private <T, R extends Row<T>> List<T> read(String type, String suffix, Duration ttl, Class<R> rowType,
                                               Supplier<List<T>> loader, Function<T, R> encode) {
        if (!properties.enabled()) {
            request(type, "bypass_disabled");
            return load(type, loader);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            request(type, "bypass_transaction");
            return load(type, loader);
        }
        String key = key(suffix);
        String value;
        try {
            value = timed(type, "get", () -> redis.opsForValue().get(key));
        } catch (RuntimeException exception) {
            request(type, "read_error");
            warn(type, "get", exception);
            return load(type, loader); // 本次 GET 已失败，不再等待 SET。
        }
        if (value != null) {
            try {
                JsonNode tree = json.readTree(value);
                if (tree == null || !tree.isArray()) throw new IllegalArgumentException("目录缓存必须为数组");
                List<R> rows = json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, rowType));
                if (rows.stream().anyMatch(x -> x == null || !x.valid())) {
                    throw new IllegalArgumentException("目录缓存字段不完整");
                }
                List<T> result = rows.stream().map(Row::response).toList();
                request(type, "hit");
                return result;
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                request(type, "decode_error");
                warn(type, "decode", exception);
            }
        } else {
            request(type, "miss");
        }
        List<T> result = load(type, loader);
        try {
            String encoded = json.writeValueAsString(result.stream().map(encode).toList());
            timed(type, "put", () -> {
                redis.opsForValue().set(key, encoded, result.isEmpty() ? properties.emptyTtl() : ttl);
                return null;
            });
            operation(type, "put", "success");
        } catch (JsonProcessingException | RuntimeException exception) {
            operation(type, "put", "error");
            warn(type, "put", exception);
        }
        return result;
    }

    private void afterCommit(String type, String suffix) {
        if (!properties.enabled()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("目录失效必须登记在写事务内");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    timed(type, "evict", () -> redis.delete(key(suffix)));
                    operation(type, "evict", "success");
                } catch (RuntimeException exception) {
                    operation(type, "evict", "error");
                    warn(type, "evict", exception);
                }
            }
        });
    }

    private String key(String suffix) { return "appoint:" + properties.namespace() + ":catalog:v1:" + suffix; }
    private void request(String type, String outcome) {
        meters.counter("catalog.cache.requests", "type", type, "outcome", outcome).increment();
    }
    private void operation(String type, String action, String outcome) {
        meters.counter("catalog.cache.operations", "type", type, "action", action, "outcome", outcome).increment();
    }
    private <T> T load(String type, Supplier<T> loader) { return timed(type, "database", loader); }
    private <T> T timed(String type, String action, Supplier<T> actionBody) {
        long start = System.nanoTime();
        try { return actionBody.get(); }
        finally { meters.timer("catalog.cache.duration", "type", type, "action", action)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS); }
    }
    private void warn(String type, String action, Exception exception) {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= TimeUnit.SECONDS.toNanos(30))
                && lastWarning.compareAndSet(previous, now)) {
            log.warn("目录缓存异常，type={}, action={}, exception={}", type, action, exception.getClass().getSimpleName());
        }
    }

    private interface Row<T> { T response(); boolean valid(); }
    private record HospitalRow(Long id, String name, String address) implements Row<HospitalResponse> {
        public HospitalResponse response() { return new HospitalResponse(id, name, address); }
        public boolean valid() { return id != null && id > 0 && name != null && address != null; }
    }
    private record DepartmentRow(Long id, String name) implements Row<DepartmentResponse> {
        public DepartmentResponse response() { return new DepartmentResponse(id, name); }
        public boolean valid() { return id != null && id > 0 && name != null; }
    }
    private record DoctorRow(Long id, String doctorNo, String name, String title, String introduction) implements Row<DoctorResponse> {
        public DoctorResponse response() { return new DoctorResponse(id, doctorNo, name, title, introduction); }
        public boolean valid() { return id != null && id > 0 && doctorNo != null && name != null && title != null; }
    }
}

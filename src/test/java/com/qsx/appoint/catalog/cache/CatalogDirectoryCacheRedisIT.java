package com.qsx.appoint.catalog.cache;

import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.service.CatalogService;
import com.qsx.appoint.catalog.service.CatalogManagementService;
import com.qsx.appoint.catalog.mapper.HospitalMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 类独占MySQL/Redis；无测试外层事务，真实验证提交/回滚与缓存回填边界。 */
@RegressionTest(properties={"appoint.catalog-cache.enabled=true", "appoint.catalog-cache.namespace=redis-it"})
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogDirectoryCacheRedisIT {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.9")).withExposedPorts(6379);
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("appoint");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);
        r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        r.add("spring.data.redis.host",REDIS::getHost);
        r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
    }
    @Autowired CatalogService catalog;
    @Autowired CatalogManagementService management;
    @Autowired CatalogDirectoryCache cache;
    @Autowired CatalogCacheProperties properties;
    @Autowired StringRedisTemplate redis;
    @Autowired TransactionTemplate transactions;
    @Autowired MeterRegistry meters;
    @MockitoSpyBean HospitalMapper hospitals;
    String key(String suffix) { return "appoint:redis-it:catalog:v1:"+suffix; }
    @BeforeEach void clearCache() {
        try (var cursor=redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions().match(key("*")).count(100).build())) {
            cursor.forEachRemaining(redis::delete);
        }
        clearInvocations(hospitals);
    }
    CreateHospitalRequest hospital(String name) {
        var r=new CreateHospitalRequest(); r.setName(name); r.setAddress("验证地址"); return r;
    }
    @Test void repeatedDirectoryReadsAvoidSqlAndPreserveLargeIdsAndNulls() {
        var first=catalog.listHospitals(); var second=catalog.listHospitals();
        assertThat(second).extracting(HospitalResponse::getId).containsExactlyElementsOf(first.stream().map(HospitalResponse::getId).toList());
        verify(hospitals,times(1)).selectList(isNull());
        assertThat(redis.getExpire(key("hospitals"))).isBetween(295L,300L);
        long large=9007199254740993L;
        var docs=cache.doctors(large,()->List.of(new DoctorResponse(large,"D001","中文医生","主任",null)));
        var again=cache.doctors(large,()->{throw new AssertionError("必须命中");});
        assertThat(again.getFirst().getId()).isEqualTo(large);
        assertThat(again.getFirst().getIntroduction()).isNull();
        assertThat(redis.getExpire(key("doctors:"+large))).isBetween(115L,120L);
        assertThat(docs.getFirst().getName()).isEqualTo(again.getFirst().getName());
        assertThat(cache.departments(large,List::of)).isEmpty();
        assertThat(redis.getExpire(key("departments:"+large))).isBetween(25L,30L);
    }
    @Test void allNineMutationsEvictOnlyRelatedListsAfterRealCommit() {
        catalog.listHospitals();
        var h=management.createHospital(hospital("缓存验收医院"));
        assertThat(redis.hasKey(key("hospitals"))).isFalse();
        catalog.listHospitals();
        var uh=new UpdateHospitalRequest(); uh.setName("新医院名"); management.updateHospital(h.getId(),uh);
        assertThat(catalog.listHospitals()).filteredOn(x->x.getId().equals(h.getId())).extracting(HospitalResponse::getName).containsExactly("新医院名");
        catalog.listDepartments(h.getId());
        var cd=new CreateDepartmentRequest(); cd.setName("测试科室"); var d=management.createDepartment(h.getId(),cd);
        assertThat(redis.hasKey(key("departments:"+h.getId()))).isFalse();
        assertThat(redis.hasKey(key("hospitals"))).isTrue();
        catalog.listDepartments(h.getId());
        var ud=new UpdateDepartmentRequest(); ud.setName("新科室名"); management.updateDepartment(d.getId(),ud);
        assertThat(redis.hasKey(key("departments:"+h.getId()))).isFalse();
        catalog.listDoctors(d.getId());
        var cr=new CreateDoctorRequest(); cr.setName("测试医生"); cr.setTitle("主任"); var doc=management.createDoctor(d.getId(),cr);
        assertThat(redis.hasKey(key("doctors:"+d.getId()))).isFalse();
        catalog.listDoctors(d.getId());
        var ur=new UpdateDoctorProfileRequest(); ur.setTitle("副主任"); management.updateDoctor(doc.getId(),ur);
        assertThat(redis.hasKey(key("doctors:"+d.getId()))).isFalse();
        catalog.listDoctors(d.getId()); management.deleteDoctor(doc.getId());
        assertThat(redis.hasKey(key("doctors:"+d.getId()))).isFalse();
        catalog.listDepartments(h.getId()); management.deleteDepartment(d.getId());
        assertThat(redis.hasKey(key("departments:"+h.getId()))).isFalse();
        catalog.listHospitals(); management.deleteHospital(h.getId());
        assertThat(redis.hasKey(key("hospitals"))).isFalse();
    }
    @Test void rollbackPreservesSharedCacheWhileTransactionReadsOwnWrites() {
        catalog.listHospitals(); String before=redis.opsForValue().get(key("hospitals"));
        transactions.executeWithoutResult(status->{
            var h=management.createHospital(hospital("待回滚医院"));
            assertThat(redis.opsForValue().get(key("hospitals"))).isEqualTo(before);
            assertThat(catalog.listHospitals()).anyMatch(x->x.getId().equals(h.getId()));
            assertThat(redis.opsForValue().get(key("hospitals"))).isEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(redis.opsForValue().get(key("hospitals"))).isEqualTo(before);
        assertThat(catalog.listHospitals()).noneMatch(x->x.getName().equals("待回滚医院"));
    }
    @Test void staleInFlightFillIsAllowedButExpiresWithoutReadRenewal() throws Exception {
        var shortProperties=new CatalogCacheProperties(true,"race",Duration.ofMillis(700),Duration.ofMillis(700),Duration.ofMillis(700),Duration.ofMillis(700));
        var shortCache=new CatalogDirectoryCache(redis,shortProperties,meters);
        var read=new CountDownLatch(1); var release=new CountDownLatch(1);
        try (var pool=Executors.newSingleThreadExecutor()) {
            Future<?> old=pool.submit(()->shortCache.hospitals(()->{
                read.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("等待提交超时"); }
                catch(InterruptedException e){ Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return List.of(new HospitalResponse(1L,"旧","地址"));
            }));
            assertThat(read.await(5,TimeUnit.SECONDS)).isTrue();
            transactions.executeWithoutResult(s->shortCache.evictHospitalsAfterCommit());
            release.countDown(); old.get(5,TimeUnit.SECONDS);
            AtomicInteger loads=new AtomicInteger();
            assertThat(shortCache.hospitals(()->{loads.incrementAndGet(); return List.of();}).getFirst().getName()).isEqualTo("旧");
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->{
                var result=shortCache.hospitals(()->{loads.incrementAndGet(); return List.of(new HospitalResponse(1L,"新","地址"));});
                assertThat(result.getFirst().getName()).isEqualTo("新");
            });
            assertThat(loads.get()).isEqualTo(1);
        } finally { release.countDown(); redis.delete("appoint:race:catalog:v1:hospitals"); }
    }
    @Test void isolatedNamespacesAndMalformedValuesCannotPoisonCatalog() {
        redis.opsForValue().set(key("hospitals"),"[null]",Duration.ofSeconds(10));
        assertThat(catalog.listHospitals()).isNotEmpty();
        assertThat(redis.opsForValue().get(key("hospitals"))).doesNotContain("[null]");
        var other=new CatalogDirectoryCache(redis,new CatalogCacheProperties(true,"other",properties.hospitalTtl(),properties.departmentTtl(),properties.doctorTtl(),properties.emptyTtl()),meters);
        try {
            assertThat(other.hospitals(()->List.of(new HospitalResponse(1L,"另一环境","地址")))).hasSize(1);
            assertThat(catalog.listHospitals()).noneMatch(x->x.getName().equals("另一环境"));
        } finally { redis.delete("appoint:other:catalog:v1:hospitals"); }
    }
    @Test void separateApplicationSharesCacheAndSeesCommittedInvalidation() {
        catalog.listHospitals();
        // Redis在第二个应用启动前已经不可达，仍必须完成启动并回源。
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        boolean resumed=false;
        try (var peer = new org.springframework.boot.builder.SpringApplicationBuilder(com.qsx.appoint.AppointApplication.class)
                .run("--spring.config.location=classpath:/regression/", "--spring.profiles.active=regression", "--server.port=0", "--spring.datasource.url="+MYSQL.getJdbcUrl(),
                        "--spring.datasource.username="+MYSQL.getUsername(),
                        "--spring.datasource.password="+MYSQL.getPassword(),
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.data.redis.host="+REDIS.getHost(),
                        "--spring.data.redis.port="+REDIS.getMappedPort(6379),
                        "--appoint.catalog-cache.enabled=true", "--appoint.catalog-cache.namespace=redis-it",
                        "--appoint.messaging.enabled=false", "--appoint.outbox.enabled=false",
                        "--appoint.expiration.enabled=false", "--appoint.reconciliation.enabled=false")) {
            var other=peer.getBean(CatalogService.class);
            var registry=peer.getBean(MeterRegistry.class);
            assertThat(other.listHospitals()).isNotEmpty();
            assertThat(registry.get("catalog.cache.requests").tag("outcome","read_error").counter().count()).isEqualTo(1);
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            resumed=true;
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->{
                other.listHospitals();
                assertThat(registry.find("catalog.cache.requests").tag("outcome","hit").counter()).isNotNull();
            });
            long loads=registry.get("catalog.cache.duration").tag("action","database").timer().count();
            other.listHospitals();
            assertThat(registry.get("catalog.cache.duration").tag("action","database").timer().count()).isEqualTo(loads);
            var h=management.createHospital(hospital("双实例共享医院"));
            try { assertThat(other.listHospitals()).anyMatch(x->x.getId().equals(h.getId())); }
            finally { management.deleteHospital(h.getId()); }
        } finally {
            if(!resumed) REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }
    @Test void unavailableRedisFallsBackAndCommittedWriteStillSucceeds() {
        catalog.listHospitals();
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        Long created=null;
        try {
            var h=management.createHospital(hospital("Redis故障仍可管理"));
            created=h.getId();
            assertThat(catalog.listHospitals()).anyMatch(x->x.getId().equals(h.getId()));
            assertThat(meters.get("catalog.cache.requests").tag("type","hospitals").tag("outcome","read_error").counter().count()).isGreaterThanOrEqualTo(1);
            assertThat(meters.get("catalog.cache.operations").tag("type","hospitals").tag("action","evict").tag("outcome","error").counter().count()).isGreaterThanOrEqualTo(1);
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            if(created!=null) management.deleteHospital(created);
        }
    }

}

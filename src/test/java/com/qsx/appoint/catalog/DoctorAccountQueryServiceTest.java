package com.qsx.appoint.catalog;

import com.qsx.appoint.catalog.mapper.DoctorAccountQueryMapper;
import com.qsx.appoint.catalog.service.DoctorAccountQueryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorAccountQueryServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "demo", "prod"})
    void seedPasswordHintUsesOnlyRecognizedEnvironmentNames(String name) {
        var mapper = mock(DoctorAccountQueryMapper.class);
        when(mapper.listAccounts()).thenReturn(List.of(new DoctorAccountQueryMapper.Row(
                1L, "医院", 2L, "科室", 3L, "DOC000003", "医生", 4L, "fixture@example.com", true)));
        var environment = new MockEnvironment().withProperty("appoint.local-environment.name", name);
        var result = new DoctorAccountQueryService(mapper, environment).listAccounts();
        assertThat(result.environment()).isEqualTo(name);
        assertThat(result.items().getFirst().passwordHint()).isEqualTo(name.equals("prod")
                ? "初始密码请联系环境维护者确认" : "初始密码见 .env." + name + " 的 LOCAL_SEED_PASSWORD");
    }
}

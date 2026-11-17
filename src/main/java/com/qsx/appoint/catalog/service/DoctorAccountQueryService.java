package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.DoctorAccountListResponse;
import com.qsx.appoint.catalog.mapper.DoctorAccountQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 只读查询实际绑定，密码说明仅使用环境名称，不读取任何凭据。 */
@Service
@RequiredArgsConstructor
public class DoctorAccountQueryService {
    private final DoctorAccountQueryMapper accounts;
    private final Environment environment;

    public DoctorAccountListResponse listAccounts() {
        String name = environment.getProperty("appoint.local-environment.name", "unknown");
        var items = accounts.listAccounts().stream().map(row -> new DoctorAccountListResponse.Item(
                row.hospitalId(), row.hospitalName(), row.departmentId(), row.departmentName(),
                row.doctorId(), row.doctorNo(), row.doctorName(), row.userId(), row.email(),
                row.userId() == null ? "UNBOUND" : "BOUND", passwordHint(row, name))).toList();
        return new DoctorAccountListResponse(name, items);
    }

    private String passwordHint(DoctorAccountQueryMapper.Row row, String name) {
        if (row.userId() == null) return "未绑定账号";
        if (!row.seededAccount()) return "使用注册时设置的密码";
        if (Set.of("dev", "test", "demo").contains(name))
            return "初始密码见 .env." + name + " 的 LOCAL_SEED_PASSWORD";
        return "初始密码请联系环境维护者确认";
    }
}

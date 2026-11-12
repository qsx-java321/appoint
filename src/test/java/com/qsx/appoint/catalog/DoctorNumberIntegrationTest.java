package com.qsx.appoint.catalog;

import com.qsx.appoint.catalog.entity.Doctor;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.service.DoctorNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring/H2验证医生编号迁移、分配和非空约束；每例回滚。 */
@SpringBootTest
@Transactional
class DoctorNumberIntegrationTest {
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private DoctorNumberGenerator doctorNumberGenerator;

    @Test
    void migrationAssignsStableUniqueNumbersToExistingDoctors() {
        assertThat(doctorMapper.selectById(1L).getDoctorNo()).isEqualTo("DOC000001");
        assertThat(doctorMapper.selectById(2L).getDoctorNo()).isEqualTo("DOC000002");
    }

    @Test
    void generatorAllocatesIncreasingNumberThatCanBeStoredOnDoctor() {
        String first = doctorNumberGenerator.nextDoctorNo();
        String second = doctorNumberGenerator.nextDoctorNo();
        assertThat(first).matches("DOC\\d{6,}");
        assertThat(Long.parseLong(second.substring(3))).isGreaterThan(Long.parseLong(first.substring(3)));

        Doctor doctor = doctor("测试医生", first);
        doctorMapper.insert(doctor);
        assertThat(doctorMapper.selectById(doctor.getId()).getDoctorNo()).isEqualTo(first);

        Doctor duplicate = doctor("重复编号医生", first);
        assertThatThrownBy(() -> doctorMapper.insert(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDoctorWithoutNumber() {
        assertThatThrownBy(() -> doctorMapper.insert(doctor("缺少编号医生", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Doctor doctor(String name, String doctorNo) {
        Doctor doctor = new Doctor();
        doctor.setDoctorNo(doctorNo);
        doctor.setDepartmentId(1L);
        doctor.setName(name);
        doctor.setTitle("医师");
        doctor.setIntroduction("测试医生资料");
        return doctor;
    }
}

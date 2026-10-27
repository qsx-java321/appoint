package com.qsx.appoint.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
    @Select("SELECT * FROM patients WHERE id = #{id} FOR UPDATE")
    Patient selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM patients WHERE user_id = #{userId} ORDER BY id DESC")
    List<Patient> selectByUserId(@Param("userId") Long userId);
}

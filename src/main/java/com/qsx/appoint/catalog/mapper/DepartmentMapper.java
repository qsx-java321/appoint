package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
    @Select("SELECT * FROM departments WHERE id = #{id} FOR UPDATE")
    Department selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM departments WHERE hospital_id = #{hospitalId}")
    long countByHospitalId(@Param("hospitalId") Long hospitalId);
}

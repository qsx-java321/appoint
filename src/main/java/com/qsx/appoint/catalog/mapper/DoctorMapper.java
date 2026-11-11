package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {
    @Select("SELECT * FROM doctors WHERE user_id = #{userId}")
    Doctor selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE doctors SET user_id = #{userId} WHERE id = #{doctorId} AND user_id IS NULL")
    int bindAccountIfUnbound(@Param("doctorId") Long doctorId, @Param("userId") Long userId);

    @Select("SELECT * FROM doctors WHERE id = #{id} FOR UPDATE")
    Doctor selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM doctors WHERE department_id = #{departmentId}")
    long countByDepartmentId(@Param("departmentId") Long departmentId);
}

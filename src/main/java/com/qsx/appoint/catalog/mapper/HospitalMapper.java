package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Hospital;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HospitalMapper extends BaseMapper<Hospital> {
    @Select("SELECT * FROM hospitals WHERE id = #{id} FOR UPDATE")
    Hospital selectByIdForUpdate(@Param("id") Long id);
}

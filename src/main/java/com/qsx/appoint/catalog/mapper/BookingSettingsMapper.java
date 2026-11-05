package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.BookingSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookingSettingsMapper extends BaseMapper<BookingSettings> {
    @Select("SELECT * FROM booking_settings WHERE id = 1 FOR UPDATE")
    BookingSettings selectForUpdate();
}

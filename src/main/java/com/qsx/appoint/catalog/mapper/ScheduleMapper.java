package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
    /** 条件更新是号源防重复占用的最终保障。 */
    @Update("UPDATE schedules SET status = 'HELD', updated_at = CURRENT_TIMESTAMP WHERE id = #{scheduleId} AND status = 'AVAILABLE'")
    int holdIfAvailable(@Param("scheduleId") Long scheduleId);

    @Update("UPDATE schedules SET status = 'BOOKED', updated_at = CURRENT_TIMESTAMP WHERE id = #{scheduleId} AND status = 'HELD'")
    int bookIfHeld(@Param("scheduleId") Long scheduleId);
}

package com.qsx.appoint.catalog.model;

/** 场次查询时的可预约状态，仅用于展示，不写入预约或场次状态机。 */
public enum ScheduleAvailability {
    AVAILABLE, FULL, CUTOFF_REACHED, STARTED
}

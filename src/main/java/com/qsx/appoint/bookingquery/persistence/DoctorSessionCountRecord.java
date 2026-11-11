package com.qsx.appoint.bookingquery.persistence;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class DoctorSessionCountRecord {
    private String sessionType;
    private int confirmedCount;
    private int notCheckedInCount;
    private int waitingCount;
    private int inProgressCount;
    private int completedCount;
}

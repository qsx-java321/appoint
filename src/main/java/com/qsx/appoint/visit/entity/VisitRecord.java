package com.qsx.appoint.visit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName("visit_records")
public class VisitRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appointmentOrderId;
    private String status;
    private LocalDateTime checkedInAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

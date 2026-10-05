package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.Stage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 追溯事件：入库→质检→分拣→库位→出库→损耗→结算一条批次链的每个节点。 */
@Getter
@Setter
@Entity
@Table(name = "biz_trace_event", indexes = {
        @Index(name = "idx_trace_batch", columnList = "batchId"),
        @Index(name = "idx_trace_lot", columnList = "lotId")
})
public class TraceEvent extends BaseEntity {
    private Long batchId;
    /** 涉及货位批次（可空） */
    private Long lotId;
    /** 涉及客户（出库/投诉节点） */
    private Long customerId;
    @Enumerated(EnumType.STRING)
    private Stage stage;
    private String title;
    @Column(columnDefinition = "text")
    private String detail;
    private String operator;
    private LocalDateTime eventAt;
}

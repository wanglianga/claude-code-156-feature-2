package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户赔付记录：分拣损耗复核中发现坏果已随货流向客户时形成。
 * 与出库后客户投诉（Complaint）相区别：本记录由复核环节主动发现并登记，
 * 由冷库对客赔付，进入客户赔付台账，并在货主账单中备注（不扣货主）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_customer_compensation")
public class CustomerCompensation extends BaseEntity {
    private String compensationNo;
    private Long reviewId;
    private Long batchId;
    /** 受偿客户（可空，表示待联系客户） */
    private Long customerId;
    /** 坏果/赔付重量（公斤） */
    private BigDecimal weight;
    /** 赔付金额（元） */
    private BigDecimal amount;
    /** 赔付原因（坏果集中筐、责任环节） */
    private String reason;
    /** 登记人 */
    private String operator;
    private LocalDateTime compensatedAt;
    /** 赔付状态：待赔付 / 已赔付 / 已核销 */
    private String status = "待赔付";
}

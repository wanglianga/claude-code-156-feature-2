package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.LossCause;
import com.county.coldchain.domain.DomainEnums.Responsibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户赔付记录：分拣损耗争议复核认定冷库责任（装卸挤压/分拣台暴露等分拣操作不当）时，
 * 冷库对货主/客户的赔付入账留痕，同时进入货主结算单与分拣班组考核。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_customer_compensation", indexes = {
        @Index(name = "idx_comp_batch", columnList = "batchId"),
        @Index(name = "idx_comp_review", columnList = "reviewId")
})
public class CustomerCompensation extends BaseEntity {
    private String compensationNo;
    private Long reviewId;
    private Long batchId;
    private Long sortingId;
    /** 受偿方：货主 ID（冷库对货主赔付） */
    private Long ownerId;
    /** 可选客户 ID（赔付最终传导给客户时） */
    private Long customerId;
    @Enumerated(EnumType.STRING)
    private Responsibility responsibility;
    @Enumerated(EnumType.STRING)
    private LossCause lossCause;
    /** 赔付重量（公斤） */
    private BigDecimal weight = BigDecimal.ZERO;
    /** 赔付单价（元/公斤） */
    private BigDecimal unitPrice;
    /** 赔付金额（元） */
    private BigDecimal amount = BigDecimal.ZERO;
    /** 责任分拣工位（进入分拣班组考核） */
    private String sortStation;
    /** 责任分拣班组（按工位归属） */
    private String sortTeam;
    @Column(columnDefinition = "text")
    private String reason;
    private String handler;
    private LocalDateTime paidAt;
    /** 是否已回写货主结算单 */
    private Boolean billWritten = false;
}

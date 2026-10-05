package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.BasketCause;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 复核坏果周转筐线索：把「筐号 + 分拣工位 + 搬运时间」连到一起，
 * 判定坏果集中来源是来货质量差、装卸挤压还是分拣台长时间暴露。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_review_basket_clue")
public class ReviewBasketClue extends BaseEntity {
    private Long reviewId;
    /** 周转筐编号 */
    private String basketNo;
    /** 分拣工位（如 A 区 3 号台） */
    private String workstation;
    /** 该筐在分拣台的开始/暴露时间 */
    private LocalDateTime sortStationAt;
    /** 搬离分拣台时间（用于计算台面上暴露时长） */
    private LocalDateTime movedAwayAt;
    /** 搬运人员/班组 */
    private String mover;
    /** 搬运环节：到货卸车 / 上台分拣 / 入库搬运 / 装车出库 */
    private String moveStage;
    /** 筐内坏果比例（0-1） */
    private java.math.BigDecimal badRate;
    /** 线索判定原因 */
    @Enumerated(EnumType.STRING)
    private BasketCause cause;
    /** 判定依据（暴露分钟数、挤压痕迹、同批次对比等） */
    private String causeBasis;
}

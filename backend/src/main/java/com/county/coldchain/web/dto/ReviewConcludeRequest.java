package com.county.coldchain.web.dto;

import com.county.coldchain.domain.DomainEnums.LossCause;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 争议复核结案：录入损耗成因、责任归属与调整后可售重量。
 * reviewedSaleableWeight 为空时按抽样坏果率自动推算。
 * crates 为周转筐证据链（筐号 + 工位 + 搬运时间串联）。
 */
@Data
public class ReviewConcludeRequest {
    /** ARRIVAL_QUALITY / LOADING_SQUEEZE / SORT_EXPOSURE / MIXED / NORMAL */
    private LossCause lossCause;
    /** OWNER 货主承担 / WAREHOUSE 冷库赔付（也可按成因自动判定，为空自动） */
    private String responsibility;
    /** 复核调整后可售重量（公斤）；为空按抽样推算 */
    private BigDecimal reviewedSaleableWeight;
    /** 手工指定复核认定损耗（公斤，优先于推算值） */
    private BigDecimal reviewedLossWeight;
    private Boolean crateConcentrated;
    private String conclusion;
    private String reviewerSignature;
    /** 冷库对客赔付时关联客户（可空，默认赔付货主） */
    private Long customerId;
    /** 责任分拣班组（可空，按工位默认） */
    private String sortTeam;
    private List<CrateItem> crates;

    @Data
    public static class CrateItem {
        private String crateNo;
        private BigDecimal badFruitWeight;
        private String sortStation;
        private String damagePattern;
        /** 该筐指向的成因（可空，由后端按搬运时间线自动判定） */
        private LossCause indicatedCause;
        private String remark;
    }
}

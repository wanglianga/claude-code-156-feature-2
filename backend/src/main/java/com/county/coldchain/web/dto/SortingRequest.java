package com.county.coldchain.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SortingRequest {
    @NotNull
    private Long batchId;
    private String sorter;
    @NotNull
    private BigDecimal inputWeight;
    private Integer laborMinutes;
    private Boolean disputed;
    private String disputeNote;
    /** 分拣视频 URL/编号（争议复核平台证据） */
    private String sortVideoUrl;
    /** 坏果照片 URL/编号，多个以逗号分隔 */
    private String badFruitPhotos;
    /** 分拣工位（如 1号分拣台） */
    private String sortStation;
    private String remark;
    @NotNull
    private List<Item> items;

    @Data
    public static class Item {
        /** A / B / PROCESS */
        @NotNull
        private String grade;
        @NotNull
        private BigDecimal weight;
        @NotNull
        private BigDecimal unitPrice;
        private Integer shelfLifeDays;
        /** 目标温区 code（可空，取品类推荐温区） */
        private String zoneCode;
    }
}

package com.county.coldchain.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 货主发起分拣损耗争议复核：证据（原重/视频/照片/等级/签名）由平台自动快照，可补充材料。 */
@Data
public class ReviewApplyRequest {
    @NotNull
    private Long sortingId;
    /** 申请人/登记人（货主或代办） */
    private String applicant;
    /** 货主主张的合理损耗率（0-1） */
    private BigDecimal claimedLossRate;
    /** 货主争议说明 */
    private String claimReason;
    /** 分拣视频材料（编号/文件名/URL，演示用文本，可空则自动生成调阅编号） */
    private String sortingVideo;
    /** 坏果照片材料（多张以逗号分隔，可空则自动生成照片编号） */
    private String badFruitPhotos;
    /** 质检员针对证据的补充说明（可空） */
    private String qcEvidenceNote;
}

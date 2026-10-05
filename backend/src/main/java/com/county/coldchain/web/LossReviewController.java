package com.county.coldchain.web;

import com.county.coldchain.domain.entity.LossReview;
import com.county.coldchain.domain.entity.ReviewSample;
import com.county.coldchain.service.LossReviewService;
import com.county.coldchain.web.dto.ReviewConcludeRequest;
import com.county.coldchain.web.dto.ReviewOpenRequest;
import com.county.coldchain.web.dto.ReviewSampleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "分拣损耗争议复核")
@RestController
@RequestMapping("/api/loss-reviews")
@RequiredArgsConstructor
public class LossReviewController {

    private final LossReviewService reviewService;

    @Operation(summary = "货主发起损耗争议复核（平台展示原入库重量/视频/坏果照片/等级/质检员签名）")
    @PostMapping
    public LossReview open(@RequestBody ReviewOpenRequest req) {
        return reviewService.open(req);
    }

    @Operation(summary = "复核单列表（status: OPEN 待复核/含抽样中，CONCLUDED 已结案，空=全部）")
    @GetMapping
    public List<LossReview> list(@RequestParam(required = false) String status) {
        return reviewService.list(status);
    }

    @Operation(summary = "复核证据包详情（分拣视频、坏果照片、等级划分、签名、抽样、周转筐证据链）")
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return reviewService.detail(id);
    }

    @Operation(summary = "复核员重新抽样（可多次，自动按累计坏果率推算复核损耗与可售重量）")
    @PostMapping("/{id}/samples")
    public ReviewSample addSample(@PathVariable Long id, @RequestBody ReviewSampleRequest req) {
        return reviewService.addSample(id, req);
    }

    @Operation(summary = "复核结案：成因判定、责任归属、调整可售重量、周转筐证据链，回写结算/考核/赔付")
    @PostMapping("/{id}/conclude")
    public LossReview conclude(@PathVariable Long id, @RequestBody ReviewConcludeRequest req) {
        return reviewService.conclude(id, req);
    }
}

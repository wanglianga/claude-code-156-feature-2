package com.county.coldchain.web;

import com.county.coldchain.domain.entity.CustomerCompensation;
import com.county.coldchain.domain.entity.LossReview;
import com.county.coldchain.domain.entity.ReviewBasketClue;
import com.county.coldchain.domain.entity.SortingAssessment;
import com.county.coldchain.service.LossReviewService;
import com.county.coldchain.web.dto.BasketClueRequest;
import com.county.coldchain.web.dto.ReviewApplyRequest;
import com.county.coldchain.web.dto.ReviewConclusionRequest;
import com.county.coldchain.web.dto.ReviewSampleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "货主发起损耗争议复核（平台自动固定证据快照）")
    @PostMapping("/apply")
    public LossReview apply(@Valid @RequestBody ReviewApplyRequest req) {
        return reviewService.apply(req);
    }

    @Operation(summary = "复核单列表（默认全部）")
    @GetMapping
    public List<LossReview> list(@RequestParam(required = false) String scope) {
        return "open".equalsIgnoreCase(scope) ? reviewService.openList() : reviewService.list();
    }

    @Operation(summary = "复核单详情（含筐号线索/考核/客户赔付/分拣证据）")
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return reviewService.detail(id);
    }

    @Operation(summary = "复核员重新抽样")
    @PostMapping("/{id}/sample")
    public LossReview sample(@PathVariable Long id, @Valid @RequestBody ReviewSampleRequest req) {
        return reviewService.sample(id, req);
    }

    @Operation(summary = "登记坏果周转筐线索（筐号+分拣工位+搬运时间串联判定）")
    @PostMapping("/{id}/baskets")
    public ReviewBasketClue addBasket(@PathVariable Long id, @Valid @RequestBody BasketClueRequest req) {
        return reviewService.addBasket(id, req);
    }

    @Operation(summary = "出具复核结论（回写可售重量/账单口径/考核/客户赔付）")
    @PostMapping("/{id}/conclude")
    public LossReview conclude(@PathVariable Long id, @Valid @RequestBody ReviewConclusionRequest req) {
        return reviewService.conclude(id, req);
    }

    @Operation(summary = "客户赔付记录置为已赔付")
    @PostMapping("/compensations/{compId}/pay")
    public CustomerCompensation pay(@PathVariable Long compId,
                                    @RequestParam(required = false) String operator) {
        return reviewService.payCompensation(compId, operator);
    }

    @Operation(summary = "质检员/分拣班组/装卸班组考核台账")
    @GetMapping("/assessments")
    public List<SortingAssessment> assessments() {
        return reviewService.assessments();
    }

    @Operation(summary = "客户赔付记录台账")
    @GetMapping("/compensations")
    public List<CustomerCompensation> compensations() {
        return reviewService.compensations();
    }
}

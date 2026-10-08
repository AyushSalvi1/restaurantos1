package com.lifeos.controller;

import com.lifeos.analytics.AnalyticsService;
import com.lifeos.analytics.BalanceScoreService;
import com.lifeos.analytics.InsightService;
import com.lifeos.analytics.PredictionService;
import com.lifeos.common.PageResponse;
import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.dto.InsightDtos;
import com.lifeos.entity.enums.InsightType;
import com.lifeos.entity.enums.PredictionType;
import com.lifeos.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Analytics, insights, predictions and the life balance score. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final InsightService insightService;
    private final PredictionService predictionService;
    private final BalanceScoreService balanceScoreService;

    public AnalyticsController(AnalyticsService analyticsService,
                               InsightService insightService,
                               PredictionService predictionService,
                               BalanceScoreService balanceScoreService) {
        this.analyticsService = analyticsService;
        this.insightService = insightService;
        this.predictionService = predictionService;
        this.balanceScoreService = balanceScoreService;
    }

    @GetMapping
    public AnalyticsDtos.AnalyticsResponse full(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.full(CurrentUser.id(), from, to);
    }

    @GetMapping("/summary")
    public AnalyticsDtos.AnalyticsSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.summary(CurrentUser.id(), from, to);
    }

    // -------------------------------------------------------------- insights

    @GetMapping("/insights")
    public PageResponse<InsightDtos.InsightResponse> insights(
            @RequestParam(required = false) InsightType type,
            @RequestParam(required = false, defaultValue = "false") boolean includeDismissed,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return PageResponse.of(insightService.page(CurrentUser.id(),
                page == null ? 0 : page, size == null ? 20 : size, type, includeDismissed));
    }

    /** Re-runs the rule engine. Rules that lack enough history report why they were skipped. */
    @PostMapping("/insights/generate")
    public InsightDtos.InsightGenerationResponse generateInsights() {
        return insightService.generate(CurrentUser.id());
    }

    @PatchMapping("/insights/{insightId}")
    public InsightDtos.InsightResponse dismiss(@PathVariable String insightId,
                                               @RequestBody InsightDtos.DismissRequest request) {
        return insightService.setDismissed(CurrentUser.id(), insightId, request.dismissed());
    }

    // ------------------------------------------------------------ predictions

    @GetMapping("/predictions")
    public List<AnalyticsDtos.PredictionResponse> predictions() {
        return predictionService.active(CurrentUser.id());
    }

    @PostMapping("/predictions/regenerate")
    public RegenerationResponse regenerate() {
        return new RegenerationResponse(predictionService.regenerate(CurrentUser.id()));
    }

    @DeleteMapping("/predictions/{predictionId}")
    public void dismissPrediction(@PathVariable String predictionId) {
        predictionService.dismiss(CurrentUser.id(), predictionId);
    }

    @GetMapping("/predictions/history")
    public List<AnalyticsDtos.PredictionResponse> history(
            @RequestParam(required = false) PredictionType type,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return predictionService.history(CurrentUser.id(), type, days == null ? 30 : days);
    }

    // --------------------------------------------------------- life balance

    @GetMapping("/balance")
    public AnalyticsDtos.BalanceScore balance() {
        return balanceScoreService.compute(CurrentUser.id());
    }

    @PutMapping("/balance/weights")
    public AnalyticsDtos.BalanceScore updateWeights(
            @Valid @RequestBody AnalyticsDtos.UpdateBalanceWeightsRequest request) {
        return balanceScoreService.updateWeights(CurrentUser.id(), request.weights());
    }

    public record RegenerationResponse(int created) {
    }
}

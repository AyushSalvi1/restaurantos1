package com.lifeos.controller;

import com.lifeos.dto.DashboardDtos;
import com.lifeos.dto.SearchDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.DashboardService;
import com.lifeos.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard and global search. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SearchService searchService;

    public DashboardController(DashboardService dashboardService, SearchService searchService) {
        this.dashboardService = dashboardService;
        this.searchService = searchService;
    }

    @GetMapping("/dashboard")
    public DashboardDtos.DashboardResponse dashboard() {
        return dashboardService.dashboard(CurrentUser.id());
    }

    @GetMapping("/today")
    public DashboardDtos.TodayResponse today() {
        return dashboardService.today(CurrentUser.id());
    }

    @GetMapping("/search")
    public SearchDtos.SearchResponse search(@RequestParam String q,
                                            @RequestParam(required = false) String type) {
        return searchService.search(CurrentUser.id(), q, type);
    }
}

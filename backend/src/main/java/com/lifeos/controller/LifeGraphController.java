package com.lifeos.controller;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.LifeGraphService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The life graph: how tasks, goals, habits, learning topics, finance and the self connect.
 *
 * <p>Several edges are derived automatically when related records change, so the graph stays useful
 * without the user maintaining links by hand. Manual links remain available for relationships the
 * system cannot infer.</p>
 */
@RestController
@RequestMapping("/api/graph")
public class LifeGraphController {

    private final LifeGraphService lifeGraphService;

    public LifeGraphController(LifeGraphService lifeGraphService) {
        this.lifeGraphService = lifeGraphService;
    }

    @GetMapping
    public AnalyticsDtos.GraphResponse graph(@RequestParam(required = false) List<GraphNodeType> types,
                                             @RequestParam(required = false) String focusType,
                                             @RequestParam(required = false) String focusId) {
        return lifeGraphService.graph(CurrentUser.id(), types, focusType, focusId);
    }

    @GetMapping("/neighbourhood")
    public AnalyticsDtos.GraphNeighbourhoodResponse neighbourhood(@RequestParam GraphNodeType type,
                                                                  @RequestParam String nodeId) {
        return lifeGraphService.neighbourhood(CurrentUser.id(), type, nodeId);
    }

    @GetMapping("/paths")
    public List<String> shortestPathToGoal(@RequestParam GraphNodeType startType,
                                           @RequestParam String startId) {
        return lifeGraphService.shortestPathToGoal(CurrentUser.id(), startType, startId);
    }

    @PostMapping("/edges")
    public AnalyticsDtos.GraphEdge createEdge(@Valid @RequestBody AnalyticsDtos.CreateEdgeRequest request) {
        return lifeGraphService.createEdge(CurrentUser.id(), request);
    }

    @DeleteMapping("/edges")
    public DeleteResult unlink(@RequestParam GraphNodeType sourceType,
                               @RequestParam String sourceId,
                               @RequestParam GraphNodeType targetType,
                               @RequestParam String targetId,
                               @RequestParam(required = false) GraphRelation relation) {
        lifeGraphService.unlink(CurrentUser.id(), sourceType, sourceId, targetType, targetId,
                relation == null ? GraphRelation.RELATED_TO : relation);
        return new DeleteResult(true);
    }

    @PostMapping("/nodes")
    public AnalyticsDtos.GraphNode createNode(@Valid @RequestBody AnalyticsDtos.CreateNodeRequest request) {
        return lifeGraphService.createPlaceholder(CurrentUser.id(), request);
    }

    @DeleteMapping("/nodes/{type}/{nodeId}")
    public DeleteResult deleteNode(@PathVariable GraphNodeType type, @PathVariable String nodeId) {
        lifeGraphService.removeNode(CurrentUser.id(), type, nodeId);
        return new DeleteResult(true);
    }

    @GetMapping("/stats")
    public GraphStats stats() {
        return new GraphStats(lifeGraphService.edgeCount(CurrentUser.id()));
    }

    public record DeleteResult(boolean removed) {
    }

    public record GraphStats(long edges) {
    }
}

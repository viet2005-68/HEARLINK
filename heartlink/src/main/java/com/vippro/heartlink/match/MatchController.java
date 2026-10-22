package com.vippro.heartlink.match;

import com.vippro.heartlink.common.security.CurrentUser;
import com.vippro.heartlink.match.dto.MatchPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {
    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public MatchPageResponse list(
            @RequestAttribute(CurrentUser.ID) Long userId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "0") @Min(0) long offset) {
        return matchService.listActive(userId, limit, offset);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @RequestAttribute(CurrentUser.ID) Long userId,
            @PathVariable("id") @Min(1) Long matchId) {
        matchService.cancel(userId, matchId);
        return ResponseEntity.noContent().build();
    }
}

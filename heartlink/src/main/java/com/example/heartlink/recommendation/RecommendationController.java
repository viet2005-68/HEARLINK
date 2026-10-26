package com.example.heartlink.recommendation;

import com.example.heartlink.common.security.CurrentUser;
import com.example.heartlink.recommendation.dto.RecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách hồ sơ chưa quẹt", description = "Loại chính mình và hồ sơ đã like/pass. Sắp xếp theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách hồ sơ"),
            @ApiResponse(responseCode = "400", description = "limit không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public RecommendationResponse list(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Parameter(description = "Từ 1 đến 50, mặc định 20") @RequestParam(defaultValue = "20") int limit) {
        return service.list(userId, limit);
    }
}

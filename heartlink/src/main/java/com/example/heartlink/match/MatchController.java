package com.example.heartlink.match;

import com.example.heartlink.common.security.CurrentUser;
import com.example.heartlink.match.dto.MatchPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "bearerAuth")
public class MatchController {
    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách match đang hoạt động",
            description = "Trả về các match active liên quan đến người dùng hiện tại, có phân trang."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách match",
                    content = @Content(schema = @Schema(implementation = MatchPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc token không hợp lệ", content = @Content)
    })
    public MatchPageResponse list(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Parameter(description = "Số lượng trên mỗi trang (1-50, mặc định 20)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @Parameter(description = "Số bản ghi bỏ qua (mặc định 0)")
            @RequestParam(defaultValue = "0") @Min(0) long offset) {
        return matchService.listActive(userId, limit, offset);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Hủy một match",
            description = "Soft-cancel match bằng cách đặt status = CANCELLED. Chỉ thành công nếu match thuộc về user hiện tại và đang active."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã hủy match", content = @Content),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc token không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "403", description = "Người dùng không thuộc match đang hoạt động này", content = @Content),
            @ApiResponse(responseCode = "404", description = "Match không tồn tại hoặc đã hủy", content = @Content)
    })
    public ResponseEntity<Void> cancel(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Parameter(description = "ID của match cần hủy") @PathVariable("id") @Min(1) Long matchId) {
        matchService.cancel(userId, matchId);
        return ResponseEntity.noContent().build();
    }
}

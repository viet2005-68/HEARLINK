package com.example.heartlink.swipe;

import com.example.heartlink.common.security.CurrentUser;
import com.example.heartlink.swipe.dto.SwipeRequest;
import com.example.heartlink.swipe.dto.SwipeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/swipes")
@SecurityRequirement(name = "bearerAuth")
public class SwipeController {
    private final SwipeService service;

    public SwipeController(SwipeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Quẹt like/pass", description = "Mỗi hồ sơ chỉ quẹt một lần. Hai bên like tạo match ngay trong response.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã lưu lượt quẹt"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc tự quẹt"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "404", description = "Hồ sơ đích không tồn tại"),
            @ApiResponse(responseCode = "409", description = "Đã quẹt hồ sơ này")
    })
    public SwipeResponse swipe(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Valid @RequestBody SwipeRequest request) {
        return service.swipe(userId, request);
    }
}

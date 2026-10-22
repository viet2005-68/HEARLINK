package com.vippro.heartlink.auth;

import com.vippro.heartlink.auth.dto.LoginRequest;
import com.vippro.heartlink.auth.dto.RefreshRequest;
import com.vippro.heartlink.auth.dto.RegisterRequest;
import com.vippro.heartlink.auth.dto.RegisterResponse;
import com.vippro.heartlink.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Đăng ký tài khoản mới",
            description = "Tạo tài khoản người dùng mới. Email sẽ được chuẩn hóa (trim + lowercase)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đăng ký thành công",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email đã được đăng ký", content = @Content)
    })
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực email/password và trả về access token + refresh token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "401", description = "Email hoặc password sai", content = @Content)
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Làm mới access token",
            description = "Sử dụng refresh_token để nhận access token mới (refresh token bị quay vòng)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Làm mới thành công",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ, hết hạn hoặc đã bị thu hồi", content = @Content)
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Đăng xuất",
            description = "Thu hồi refresh token (idempotent: token không tồn tại hoặc đã thu hồi cũng không báo lỗi)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã đăng xuất", content = @Content),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ", content = @Content)
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

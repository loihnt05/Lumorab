package com.lumora.identity;

import com.lumora.identity.security.CurrentUser;
import com.lumora.identity.security.LocalJwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "auth-controller", description = "Đăng nhập và phân quyền hệ thống Lumora")
public class AuthController {
  private final UserRepository users;
  private final UserQueryService userQueries;
  private final LocalJwtService jwtService;

  public record LoginRequest(@NotBlank @Email String email, String password) {}

  public record RegisterRequest(
      @NotBlank @Email String email, @NotBlank String fullName, String phone) {}

  public record AuthResponse(String token, UserProfile user) {}

  public record DemoAccount(
      UUID id, String email, String fullName, String role, String roleLabel, String description) {}

  @PostMapping("/login")
  @Operation(summary = "Đăng nhập bằng Email, nhận Bearer token và User Profile")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    String cleanEmail = request.email().trim().toLowerCase();
    LocalUser user =
        users
            .findByEmailIgnoreCase(cleanEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản với email: " + cleanEmail));

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Tài khoản của bạn hiện đang bị vô hiệu hóa.");
    }

    String token = jwtService.generateToken(user);
    return ResponseEntity.ok(new AuthResponse(token, UserProfile.from(user)));
  }

  @PostMapping("/register")
  @Operation(summary = "Đăng ký tài khoản khách hàng mới")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    String cleanEmail = request.email().trim().toLowerCase();
    if (users.existsByEmailIgnoreCase(cleanEmail)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Email này đã được đăng ký tài khoản trong hệ thống.");
    }

    String[] nameParts = request.fullName().trim().split("\\s+", 2);
    String firstName = nameParts.length > 1 ? nameParts[1] : nameParts[0];
    String lastName = nameParts.length > 1 ? nameParts[0] : "";

    String clerkUserId =
        "user_reg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    LocalUser newUser =
        LocalUser.builder()
            .clerkUserId(clerkUserId)
            .email(cleanEmail)
            .firstName(firstName)
            .lastName(lastName)
            .phone(request.phone() != null ? request.phone().trim() : null)
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .build();

    LocalUser saved = users.save(newUser);
    String token = jwtService.generateToken(saved);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new AuthResponse(token, UserProfile.from(saved)));
  }

  @GetMapping("/demo-accounts")
  @Operation(summary = "Lấy danh sách các tài khoản Demo có sẵn trong hệ thống")
  public List<DemoAccount> demoAccounts() {
    return users.findAll().stream()
        .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getEmail() != null)
        .sorted(
            Comparator.comparing(
                (LocalUser u) ->
                    u.getRole() == Role.ADMIN ? 0 : u.getRole() == Role.MANAGER ? 1 : 2))
        .map(
            u -> {
              String fullName =
                  (u.getLastName() != null ? u.getLastName() + " " : "")
                      + (u.getFirstName() != null ? u.getFirstName() : "");
              if (fullName.isBlank()) fullName = u.getEmail();

              String roleLabel =
                  switch (u.getRole()) {
                    case ADMIN -> "Quản trị viên toàn hệ thống (Admin)";
                    case MANAGER -> "Quản lý chi nhánh homestay (Manager)";
                    case USER -> "Khách hàng cá nhân (Customer)";
                  };

              String description =
                  switch (u.getEmail()) {
                    case "owner@lumora.vn" ->
                        "Toàn quyền quản trị, cấu hình giá/phòng, giám sát hệ thống";
                    case "manager.dalat@lumora.vn" ->
                        "Phụ trách vận hành, nhận/trả phòng tại chi nhánh Đà Lạt";
                    case "manager.saigon@lumora.vn" ->
                        "Phụ trách vận hành, nhận/trả phòng tại chi nhánh Sài Gòn";
                    case "manager.coast@lumora.vn" ->
                        "Phụ trách vận hành tại chi nhánh Vũng Tàu & Hà Nội";
                    case "loitn@lumora.vn" ->
                        "Khách hàng thân thiết có lịch sử booking đã hoàn thành";
                    default -> "Tài khoản khách hàng lưu trú";
                  };

              return new DemoAccount(
                  u.getId(),
                  u.getEmail(),
                  fullName.trim(),
                  u.getRole().name(),
                  roleLabel,
                  description);
            })
        .toList();
  }

  @GetMapping("/me")
  @Operation(summary = "Lấy thông tin tài khoản hiện tại từ Bearer Token")
  public UserProfile me(@AuthenticationPrincipal CurrentUser user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
    }
    return userQueries.find(user.id());
  }
}

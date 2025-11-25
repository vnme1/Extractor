package com.securedoc.extractor.controller;

import com.securedoc.extractor.model.User;
import com.securedoc.extractor.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 모든 사용자 조회 (관리자 전용)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    /**
     * 새 사용자 추가 (관리자 전용)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            // 중복 체크
            if (userRepository.existsByUsername(request.getUsername())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "이미 존재하는 사용자명입니다.");
                return ResponseEntity.badRequest().body(error);
            }

            if (userRepository.existsByEmail(request.getEmail())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "이미 존재하는 이메일입니다.");
                return ResponseEntity.badRequest().body(error);
            }

            // 새 사용자 생성
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(request.getRole())
                    .build();

            userRepository.save(user);
            log.info("새 사용자 추가: {} ({})", user.getUsername(), user.getRole());

            return ResponseEntity.ok(user);

        } catch (Exception e) {
            log.error("사용자 추가 실패", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "사용자 추가 중 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 사용자 역할 변경 (관리자 전용)
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> changeUserRole(
            @PathVariable Long userId,
            @RequestBody RoleChangeRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        user.setRole(request.getRole());
        userRepository.save(user);

        log.info("사용자 역할 변경: {} -> {}", user.getUsername(), request.getRole());

        return ResponseEntity.ok(user);
    }

    /**
     * 사용자 활성/비활성 상태 변경 (관리자 전용)
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> changeUserStatus(
            @PathVariable Long userId,
            @RequestBody StatusChangeRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        user.setEnabled(request.isEnabled());
        userRepository.save(user);

        log.info("사용자 상태 변경: {} -> {}", user.getUsername(), request.isEnabled() ? "활성" : "비활성");

        return ResponseEntity.ok(user);
    }

    @Data
    static class CreateUserRequest {
        private String username;
        private String email;
        private String password;
        private User.Role role;
    }

    @Data
    static class RoleChangeRequest {
        private User.Role role;
    }

    @Data
    static class StatusChangeRequest {
        private boolean enabled;
    }
}

package com.securedoc.extractor.config;

import com.securedoc.extractor.model.User;
import com.securedoc.extractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 기본 관리자 계정 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // admin 계정이 이미 존재하는지 확인
        if (userRepository.existsByUsername("admin")) {
            log.info("기본 관리자 계정(admin)이 이미 존재합니다.");
            return;
        }

        // 관리자 역할 계정이 없으면 기본 관리자 생성
        if (!userRepository.existsByRole(User.Role.ADMIN)) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@securedoc.com")
                    .password(passwordEncoder.encode("admin123!"))
                    .role(User.Role.ADMIN)
                    .build();

            userRepository.save(admin);
            log.info("=".repeat(80));
            log.info("기본 관리자 계정이 생성되었습니다.");
            log.info("사용자명: admin");
            log.info("비밀번호: admin123!");
            log.info("보안을 위해 반드시 비밀번호를 변경해주세요!");
            log.info("=".repeat(80));
        } else {
            log.info("관리자 역할 계정이 이미 존재합니다.");
        }
    }
}

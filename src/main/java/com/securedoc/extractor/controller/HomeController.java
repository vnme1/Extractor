package com.securedoc.extractor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 루트 경로 처리 컨트롤러
 */
@Controller
public class HomeController {

    /**
     * 루트 경로 접근 시 로그인 페이지로 리다이렉트
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/login.html";
    }
}

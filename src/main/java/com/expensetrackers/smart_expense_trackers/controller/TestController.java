package com.expensetrackers.smart_expense_trackers.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestController {

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Test page is working!";
    }

    @GetMapping("/test-page")
    public String testPage() {
        return "test"; // We'll create a simple test.html
    }
}
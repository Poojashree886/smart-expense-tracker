package com.expensetrackers.smart_expense_trackers.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
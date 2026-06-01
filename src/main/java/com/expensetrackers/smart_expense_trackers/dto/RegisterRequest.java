package com.expensetrackers.smart_expense_trackers.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String currency;
}
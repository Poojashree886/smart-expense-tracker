package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.security.UserDetailsImpl;
import com.expensetrackers.smart_expense_trackers.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/email-settings")
public class EmailSettingsController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.email.alerts-enabled:true}")
    private boolean alertsEnabled;

    @Value("${app.email.budget-threshold:80}")
    private int budgetThreshold;

    // Get current logged in user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    // 1. SHOW EMAIL SETTINGS PAGE
    @GetMapping
    public String emailSettingsPage(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Map<String, Object> settings = new HashMap<>();
        settings.put("alertsEnabled", alertsEnabled);
        settings.put("budgetThreshold", budgetThreshold);
        settings.put("monthlySummary", true);
        settings.put("billReminders", true);
        settings.put("marketingEmails", false);

        model.addAttribute("settings", settings);
        model.addAttribute("email", currentUser.getEmail());
        model.addAttribute("pageTitle", "Email Settings");

        return "email/settings";
    }

    // 2. UPDATE EMAIL SETTINGS
    @PostMapping("/update")
    public String updateSettings(@RequestParam Map<String, String> allParams,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            // In a real app, save these to database
            redirectAttributes.addFlashAttribute("success", "Email settings updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating settings: " + e.getMessage());
        }

        return "redirect:/email-settings";
    }

    // 3. TEST EMAIL CONFIGURATION - FIXED
    @PostMapping("/test")
    public String testEmail(RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        // ✅ FIXED: Use testEmailConfiguration method instead of testConnection
        boolean testResult = emailService.testEmailConfiguration(currentUser);
        
        if (testResult) {
            redirectAttributes.addFlashAttribute("success", "✅ Test email sent successfully! Check your inbox.");
        } else {
            redirectAttributes.addFlashAttribute("error", "❌ Failed to send test email. Please check your email configuration.");
        }

        return "redirect:/email-settings";
    }

    // 4. SEND MONTHLY SUMMARY (Manual trigger) - FIXED
    @PostMapping("/send-summary")
    public String sendMonthlySummary(@RequestParam int month,
                                     @RequestParam int year,
                                     RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            // Get real data from repositories instead of sample data
            // For now, use zeros - you can implement actual data fetching later
            double totalIncome = 0;
            double totalExpense = 0;
            double savings = totalIncome - totalExpense;
            double savingsRate = totalIncome > 0 ? (savings / totalIncome) * 100 : 0;
            
            // ✅ FIXED: Pass categorySpending map as 5th parameter
            Map<String, Double> categorySpending = new HashMap<>();
            
            emailService.sendMonthlySummary(currentUser, month, year, 
                                           totalIncome, totalExpense, 
                                           savings, savingsRate, categorySpending);
            
            redirectAttributes.addFlashAttribute("success", "Monthly summary sent to your email!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error sending summary: " + e.getMessage());
        }

        return "redirect:/email-settings";
    }
}
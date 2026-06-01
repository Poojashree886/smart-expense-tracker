package com.expensetrackers.smart_expense_trackers.config;

import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.TransactionRepository;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@EnableScheduling
public class ScheduledEmailService {

    @Autowired
    private EmailService emailService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    // This runs on the 1st day of every month at 9:00 AM
    @Scheduled(cron = "0 0 9 1 * ?")
    public void sendMonthlySummaries() {
        System.out.println("📧 Starting to send monthly summaries to all users...");
        
        // Get last month's data
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int month = lastMonth.getMonthValue();
        int year = lastMonth.getYear();
        
        // Get all users
        List<User> allUsers = userRepository.findAll();
        int emailsSent = 0;
        
        for (User user : allUsers) {
            try {
                // Calculate user's monthly totals
                Double totalIncome = transactionRepository.getTotalIncomeByMonth(user.getId(), month, year);
                Double totalExpense = transactionRepository.getTotalExpenseByMonth(user.getId(), month, year);
                
                double income = totalIncome != null ? totalIncome : 0.0;
                double expense = totalExpense != null ? totalExpense : 0.0;
                double savings = income - expense;
                double savingsRate = income > 0 ? (savings / income) * 100 : 0;
                
                // Only send email if user has activity
                if (income > 0 || expense > 0) {
                    // Get category spending (you can expand this later)
                    Map<String, Double> categorySpending = new HashMap<>();
                    
                    // Send the monthly summary email
                    emailService.sendMonthlySummary(user, month, year, income, expense, savings, savingsRate, categorySpending);
                    emailsSent++;
                    System.out.println("✅ Monthly summary sent to: " + user.getEmail());
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to send monthly summary to: " + user.getEmail());
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        System.out.println("📧 Monthly summaries completed. Sent " + emailsSent + " emails.");
    }
}
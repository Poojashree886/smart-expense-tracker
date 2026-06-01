package com.expensetrackers.smart_expense_trackers.service;

import com.expensetrackers.smart_expense_trackers.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.alerts-enabled:true}")
    private boolean alertsEnabled;

    @Value("${app.email.budget-threshold:80}")
    private int budgetThreshold;

    // Send budget exceeded alert
    public void sendBudgetExceededAlert(User user, String category, double budgetAmount, double spentAmount) {
        if (!alertsEnabled) return;

        double exceededBy = spentAmount - budgetAmount;
        
        String subject = "⚠️ BUDGET ALERT: " + category + " budget exceeded!";
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     ⚠️ BUDGET EXCEEDED ALERT ⚠️\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Your %s budget has been exceeded!\n\n" +
            "📊 Budget Details:\n" +
            "   • Budget Amount:      ₹%.2f\n" +
            "   • Actual Spent:       ₹%.2f\n" +
            "   • Exceeded By:        ₹%.2f\n" +
            "   • Month/Year:         %s\n\n" +
            "💡 Recommendations:\n" +
            "   • Review your recent %s expenses\n" +
            "   • Consider increasing your budget for this category\n" +
            "   • Look for areas to cut back\n\n" +
            "Log in to your Smart Expense Tracker to view detailed reports.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Smart Expense Tracker - Stay on top of your finances!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName(),
            category, budgetAmount, spentAmount, exceededBy,
            getCurrentMonthYear(),
            category
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Send budget warning alert (when spending > threshold%)
    public void sendBudgetWarningAlert(User user, String category, double budgetAmount, double spentAmount) {
        if (!alertsEnabled) return;

        double percentageUsed = (spentAmount / budgetAmount) * 100;
        double remainingAmount = budgetAmount - spentAmount;
        
        String subject = "⚠️ BUDGET WARNING: " + category + " at " + String.format("%.0f", percentageUsed) + "%";
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     ⚠️ BUDGET WARNING ⚠️\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Your %s budget has reached %.0f%% of its limit!\n\n" +
            "📊 Budget Details:\n" +
            "   • Budget Amount:      ₹%.2f\n" +
            "   • Spent So Far:       ₹%.2f\n" +
            "   • Remaining:          ₹%.2f\n" +
            "   • Usage:              %.0f%%\n" +
            "   • Month/Year:         %s\n\n" +
            "💡 Recommendations:\n" +
            "   • Reduce spending in this category\n" +
            "   • Look for cheaper alternatives\n" +
            "   • You have ₹%.2f left to spend\n\n" +
            "Continue tracking your expenses to stay within budget!\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Smart Expense Tracker - Stay on top of your finances!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName(),
            category, percentageUsed,
            budgetAmount, spentAmount, remainingAmount, percentageUsed,
            getCurrentMonthYear(),
            remainingAmount
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Send monthly summary report
    public void sendMonthlySummary(User user, int month, int year, 
                                   double totalIncome, double totalExpense, 
                                   double savings, double savingsRate,
                                   java.util.Map<String, Double> categorySpending) {
        if (!alertsEnabled) return;

        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                               "July", "August", "September", "October", "November", "December"};
        
        String subject = "📊 Monthly Financial Summary - " + monthNames[month-1] + " " + year;
        
        // Build category breakdown string
        StringBuilder categoryBreakdown = new StringBuilder();
        if (categorySpending != null && !categorySpending.isEmpty()) {
            categoryBreakdown.append("\n📂 Top Spending Categories:\n");
            categorySpending.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(entry -> {
                    categoryBreakdown.append(String.format("   • %-20s: ₹%.2f\n", entry.getKey(), entry.getValue()));
                });
        } else {
            categoryBreakdown.append("\n📂 No spending recorded this month.\n");
        }
        
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     📊 MONTHLY FINANCIAL SUMMARY\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "Here's your financial summary for %s %d:\n\n" +
            "💰 INCOME & EXPENSES:\n" +
            "   • Total Income:       ₹%.2f\n" +
            "   • Total Expense:      ₹%.2f\n" +
            "   • Savings:            ₹%.2f\n" +
            "   • Savings Rate:       %.1f%%\n\n" +
            "%s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "💡 Tips for Next Month:\n" +
            "   • Try to increase your savings rate by 5%%\n" +
            "   • Review your top spending categories\n" +
            "   • Set realistic budgets for each category\n\n" +
            "Log in to your Smart Expense Tracker for detailed insights!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Smart Expense Tracker - Stay on top of your finances!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName(),
            monthNames[month-1], year,
            totalIncome,
            totalExpense,
            savings,
            savingsRate,
            categoryBreakdown.toString()
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Send welcome email to new users
    public void sendWelcomeEmail(User user) {
        String subject = "🎉 Welcome to Smart Expense Tracker!";
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     🎉 WELCOME TO SMART EXPENSE TRACKER!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "We're excited to help you take control of your finances!\n\n" +
            "✨ What you can do with Smart Expense Tracker:\n" +
            "   ✓ Track your daily expenses and income\n" +
            "   ✓ Create budgets for different categories\n" +
            "   ✓ Get email alerts when you exceed budgets\n" +
            "   ✓ View detailed reports and analytics\n" +
            "   ✓ Manage your bills and due dates\n\n" +
            "🚀 Getting Started:\n" +
            "   1. Add your first transaction\n" +
            "   2. Set up your monthly budgets\n" +
            "   3. Explore the dashboard for insights\n" +
            "   4. Configure email alerts in Settings\n\n" +
            "💡 Pro Tip: Set realistic budgets and review them monthly!\n\n" +
            "If you need any help, check out our FAQ section.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Happy tracking!\n" +
            "The Smart Expense Tracker Team\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName()
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Send transaction added confirmation
    public void sendTransactionConfirmation(User user, String category, String type, double amount, String date) {
        if (!alertsEnabled) return;

        String subject = "💰 Transaction Added: " + type + " - " + category;
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     ✅ TRANSACTION CONFIRMATION\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "A new transaction has been recorded:\n\n" +
            "   📅 Date:        %s\n" +
            "   📂 Category:    %s\n" +
            "   💵 Type:        %s\n" +
            "   💰 Amount:      ₹%.2f\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "View all your transactions at:\n" +
            "http://localhost:8081/transactions\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Smart Expense Tracker - Stay on top of your finances!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName(),
            date, category, type, amount
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Send bill reminder
    public void sendBillReminder(User user, String billName, double amount, int daysUntilDue) {
        if (!alertsEnabled) return;

        String urgency = daysUntilDue <= 1 ? "URGENT" : "Reminder";
        String subject = "🔔 BILL REMINDER: " + billName + " - " + urgency;
        
        String body = String.format(
            "Dear %s,\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     🔔 BILL %s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "This is a reminder that your %s bill is due soon!\n\n" +
            "📋 Bill Details:\n" +
            "   • Bill Name:       %s\n" +
            "   • Amount:          ₹%.2f\n" +
            "   • Days Until Due:  %d day(s)\n\n" +
            "💡 Action Required:\n" +
            "   ✓ Ensure sufficient funds are available\n" +
            "   ✓ Mark as paid after payment\n" +
            "   ✓ Set up auto-pay if available\n\n" +
            "Log in to mark this bill as paid:\n" +
            "http://localhost:8081/bills\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Smart Expense Tracker - Never miss a payment!\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            user.getFullName(),
            urgency,
            billName,
            billName,
            amount,
            daysUntilDue
        );

        sendEmail(user.getEmail(), subject, body);
    }

    // Test email configuration
    public boolean testEmailConfiguration(User user) {
        try {
            String subject = "✅ Email Configuration Test - Smart Expense Tracker";
            String body = String.format(
                "Dear %s,\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "     ✅ EMAIL CONFIGURATION SUCCESSFUL!\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "This is a test email to confirm that your email settings are working correctly.\n\n" +
                "You will now receive real-time alerts for:\n" +
                "   • Budget exceeded warnings\n" +
                "   • Monthly financial summaries\n" +
                "   • Bill payment reminders\n" +
                "   • Transaction confirmations\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "Smart Expense Tracker - Stay on top of your finances!\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                user.getFullName()
            );
            
            sendEmail(user.getEmail(), subject, body);
            return true;
        } catch (Exception e) {
            System.err.println("Test email failed: " + e.getMessage());
            return false;
        }
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            System.out.println("✅ Email sent to: " + to);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email to: " + to);
            System.err.println("Error: " + e.getMessage());
        }
    }

    private String getCurrentMonthYear() {
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy");
        return now.format(formatter);
    }
}
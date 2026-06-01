package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.Bill;
import com.expensetrackers.smart_expense_trackers.entity.Transaction;
import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.BillRepository;
import com.expensetrackers.smart_expense_trackers.repository.TransactionRepository;
import com.expensetrackers.smart_expense_trackers.repository.BudgetRepository;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
public class DashboardController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BillRepository billRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        // Summary Cards Data
        Map<String, Object> summaryData = getSummaryData(userId);
        model.addAttribute("totalIncome", summaryData.get("totalIncome"));
        model.addAttribute("totalExpense", summaryData.get("totalExpense"));
        model.addAttribute("savings", summaryData.get("savings"));
        model.addAttribute("budgetRemaining", summaryData.get("budgetRemaining"));

        // Quick Stats - Now with REAL data
        model.addAttribute("highestCategory", getHighestExpenseCategory(userId));
        model.addAttribute("dailyAverage", getDailyAverageSpending(userId));
        model.addAttribute("budgetHealthScore", getBudgetHealthScore(userId));
        
        // Bills and Budgets
        model.addAttribute("upcomingBills", getUpcomingBills(userId));
        model.addAttribute("budgets", getBudgetProgress(userId));
        
        // Chart Data
        model.addAttribute("chartLabels", getChartLabels());
        model.addAttribute("thisWeekData", getThisWeekData(userId));
        model.addAttribute("lastWeekData", getLastWeekData(userId));
        model.addAttribute("hasTransactions", hasTransactions(userId));
        
        // Transactions and User Info
        model.addAttribute("recentTransactions", getRecentTransactions(userId));
        model.addAttribute("exceededBudgets", getExceededBudgets(userId));
        model.addAttribute("user", getUserInfo(userId));

        return "dashboard";
    }

    // ==================== SUMMARY CARDS ====================
    private Map<String, Object> getSummaryData(Long userId) {
        Map<String, Object> data = new HashMap<>();
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal totalIncome = calculateTotalIncome(userId, currentMonth, currentYear);
        BigDecimal totalExpense = calculateTotalExpense(userId, currentMonth, currentYear);
        BigDecimal savings = totalIncome.subtract(totalExpense);
        BigDecimal budgetRemaining = calculateBudgetRemaining(userId, currentMonth, currentYear);

        data.put("totalIncome", formatCurrency(totalIncome));
        data.put("totalExpense", formatCurrency(totalExpense));
        data.put("savings", formatCurrency(savings));
        data.put("budgetRemaining", formatCurrency(budgetRemaining));

        return data;
    }

    // ==================== QUICK STATS - NOW WITH REAL DATA ====================
    private String getHighestExpenseCategory(Long userId) {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        
        // Get all transactions for current month
        List<Transaction> transactions = transactionRepository.findByUserIdAndMonthAndYear(userId, currentMonth, currentYear);
        
        // Group by category and sum amounts
        Map<String, BigDecimal> categorySpending = new HashMap<>();
        
        for (Transaction t : transactions) {
            if ("EXPENSE".equals(t.getType()) && t.getCategory() != null) {
                String categoryName = t.getCategory().getName();
                categorySpending.put(categoryName, 
                    categorySpending.getOrDefault(categoryName, BigDecimal.ZERO)
                        .add(t.getAmount()));
            }
        }
        
        if (categorySpending.isEmpty()) {
            return "No data";
        }
        
        // Find the category with highest spending
        Map.Entry<String, BigDecimal> highest = categorySpending.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        
        if (highest != null) {
            return highest.getKey() + " (₹" + formatCurrency(highest.getValue()) + ")";
        }
        
        return "No data";
    }

    private String getDailyAverageSpending(Long userId) {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        
        BigDecimal totalExpense = calculateTotalExpense(userId, currentMonth, currentYear);
        
        if (totalExpense.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        
        // Get number of days passed in current month
        int daysPassed = now.getDayOfMonth();
        
        // Calculate average based on days passed
        BigDecimal dailyAvg = totalExpense.divide(new BigDecimal(daysPassed), 0, RoundingMode.HALF_UP);
        
        return formatCurrency(dailyAvg);
    }

    private int getBudgetHealthScore(Long userId) {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        
        BigDecimal totalBudget = calculateTotalBudget(userId, currentMonth, currentYear);
        BigDecimal totalExpense = calculateTotalExpense(userId, currentMonth, currentYear);
        
        if (totalBudget.compareTo(BigDecimal.ZERO) == 0) {
            return 0; // No budgets set
        }
        
        // Calculate health score: (budget - expense) / budget * 100
        BigDecimal remaining = totalBudget.subtract(totalExpense);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return 0; // Overspent
        }
        
        return remaining.multiply(new BigDecimal(100))
                .divide(totalBudget, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    // ==================== UPCOMING BILLS ====================
    private List<Map<String, Object>> getUpcomingBills(Long userId) {
        List<Map<String, Object>> bills = new ArrayList<>();
        
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        
        // Get unpaid bills due in the next 7 days
        List<Bill> upcomingFromDb = billRepository.findBillsDueBetween(userId, today, nextWeek);
        
        // Add upcoming bills
        for (Bill bill : upcomingFromDb) {
            if (!bill.getIsPaid()) {
                Map<String, Object> billMap = createBillMap(bill, today);
                bills.add(billMap);
            }
        }
        
        // Sort by due date (closest first)
        bills.sort((b1, b2) -> {
            String date1 = (String) b1.get("sortDate");
            String date2 = (String) b2.get("sortDate");
            return date1.compareTo(date2);
        });
        
        // Limit to 5 bills
        if (bills.size() > 5) {
            bills = bills.subList(0, 5);
        }
        
        return bills;
    }
    
    private Map<String, Object> createBillMap(Bill bill, LocalDate today) {
        Map<String, Object> billMap = new HashMap<>();
        billMap.put("billName", bill.getBillName());
        billMap.put("amount", String.format("%,.0f", bill.getAmount()));
        billMap.put("sortDate", bill.getDueDate().toString());
        
        long daysUntilDue = ChronoUnit.DAYS.between(today, bill.getDueDate());
        String dueText;
        
        if (daysUntilDue < 0) {
            dueText = Math.abs(daysUntilDue) + " days overdue";
        } else if (daysUntilDue == 0) {
            dueText = "Due today";
        } else if (daysUntilDue == 1) {
            dueText = "Due tomorrow";
        } else {
            dueText = "Due in " + daysUntilDue + " days";
        }
        
        billMap.put("dueDate", dueText);
        billMap.put("dueText", dueText);
        
        return billMap;
    }

    // ==================== BUDGET PROGRESS - NOW WITH REAL DATA ====================
    private List<Map<String, Object>> getBudgetProgress(Long userId) {
        List<Map<String, Object>> budgets = new ArrayList<>();
        
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        
        // Get all budgets for current month
        List<com.expensetrackers.smart_expense_trackers.entity.Budget> userBudgets = 
                budgetRepository.findByUserIdAndMonthAndYear(userId, currentMonth, currentYear);
        
        for (com.expensetrackers.smart_expense_trackers.entity.Budget budget : userBudgets) {
            if (budget.getCategory() != null) {
                Map<String, Object> budgetMap = new HashMap<>();
                budgetMap.put("category", budget.getCategory().getName());
                
                BigDecimal budgetAmount = budget.getAmount();
                BigDecimal spentAmount = calculateSpentForCategory(userId, budget.getCategory().getId(), currentMonth, currentYear);
                
                budgetMap.put("total", formatCurrency(budgetAmount));
                budgetMap.put("spent", formatCurrency(spentAmount));
                
                int percentage = 0;
                if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
                    percentage = spentAmount.multiply(new BigDecimal(100))
                            .divide(budgetAmount, 0, RoundingMode.HALF_UP)
                            .intValue();
                }
                budgetMap.put("percentage", percentage);
                
                if (percentage > 100) {
                    BigDecimal exceeded = spentAmount.subtract(budgetAmount);
                    budgetMap.put("exceededAmount", formatCurrency(exceeded));
                }
                
                budgets.add(budgetMap);
            }
        }
        
        return budgets;
    }
    
    private BigDecimal calculateSpentForCategory(Long userId, Long categoryId, int month, int year) {
        Double spent = transactionRepository.getTotalExpenseByCategoryAndMonth(userId, categoryId, month, year);
        return spent != null ? BigDecimal.valueOf(spent) : BigDecimal.ZERO;
    }

    // ==================== CHART DATA ====================
    private String[] getChartLabels() {
        LocalDate now = LocalDate.now();
        String[] labels = new String[7];
        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            labels[6-i] = date.getDayOfWeek().toString().substring(0, 3);
        }
        return labels;
    }

    private List<Double> getThisWeekData(Long userId) {
        List<Double> data = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            Double expense = getTotalExpenseByDate(userId, date);
            data.add(expense != null ? expense : 0.0);
        }
        return data;
    }

    private List<Double> getLastWeekData(Long userId) {
        List<Double> data = new ArrayList<>();
        LocalDate lastWeek = LocalDate.now().minusWeeks(1);
        for (int i = 6; i >= 0; i--) {
            LocalDate date = lastWeek.minusDays(i);
            Double expense = getTotalExpenseByDate(userId, date);
            data.add(expense != null ? expense : 0.0);
        }
        return data;
    }
    
    private Double getTotalExpenseByDate(Long userId, LocalDate date) {
        List<Transaction> transactions = transactionRepository.findByUserIdAndDate(userId, date);
        return transactions.stream()
                .filter(t -> "EXPENSE".equals(t.getType()))
                .mapToDouble(t -> t.getAmount().doubleValue())
                .sum();
    }
    
    private boolean hasTransactions(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByDateDesc(userId);
        return !transactions.isEmpty();
    }

    // ==================== RECENT TRANSACTIONS ====================
    private List<Map<String, Object>> getRecentTransactions(Long userId) {
        List<Map<String, Object>> transactions = new ArrayList<>();
        
        List<Transaction> recentFromDb = transactionRepository.findByUserIdOrderByDateDesc(userId);
        
        int limit = Math.min(recentFromDb.size(), 5);
        for (int i = 0; i < limit; i++) {
            Transaction t = recentFromDb.get(i);
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("id", t.getId());
            transaction.put("date", t.getDate().toString());
            transaction.put("category", t.getCategory() != null ? t.getCategory().getName() : "Unknown");
            transaction.put("type", t.getType());
            transaction.put("amount", t.getAmount().intValue());
            transaction.put("note", t.getNote() != null ? t.getNote() : "");
            transactions.add(transaction);
        }
        
        return transactions;
    }

    // ==================== EXCEEDED BUDGETS ====================
    private List<String> getExceededBudgets(Long userId) {
        List<String> exceeded = new ArrayList<>();
        List<Map<String, Object>> budgets = getBudgetProgress(userId);
        
        for (Map<String, Object> budget : budgets) {
            Integer percentage = (Integer) budget.get("percentage");
            if (percentage != null && percentage > 100) {
                exceeded.add((String) budget.get("category"));
            }
        }
        return exceeded;
    }

    // ==================== USER INFO ====================
    private Map<String, Object> getUserInfo(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Map<String, Object> userInfo = new HashMap<>();
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            userInfo.put("fullName", user.getFullName());
            userInfo.put("email", user.getEmail());
            userInfo.put("currency", user.getCurrency());
            userInfo.put("username", user.getUsername());
        } else {
            userInfo.put("fullName", "User");
            userInfo.put("email", "user@example.com");
            userInfo.put("currency", "INR");
            userInfo.put("username", "user");
        }
        
        return userInfo;
    }

    // ==================== CALCULATION METHODS ====================
    private BigDecimal calculateTotalIncome(Long userId, int month, int year) {
        Double income = transactionRepository.getTotalIncomeByMonth(userId, month, year);
        return income != null ? BigDecimal.valueOf(income) : BigDecimal.ZERO;
    }

    private BigDecimal calculateTotalExpense(Long userId, int month, int year) {
        Double expense = transactionRepository.getTotalExpenseByMonth(userId, month, year);
        return expense != null ? BigDecimal.valueOf(expense) : BigDecimal.ZERO;
    }

    private BigDecimal calculateTotalBudget(Long userId, int month, int year) {
        Double budget = budgetRepository.getTotalBudgetByMonth(userId, month, year);
        return budget != null ? BigDecimal.valueOf(budget) : BigDecimal.ZERO;
    }

    private BigDecimal calculateBudgetRemaining(Long userId, int month, int year) {
        Double totalBudget = budgetRepository.getTotalBudgetByMonth(userId, month, year);
        Double totalExpense = transactionRepository.getTotalExpenseByMonth(userId, month, year);
        
        BigDecimal budget = totalBudget != null ? BigDecimal.valueOf(totalBudget) : BigDecimal.ZERO;
        BigDecimal expense = totalExpense != null ? BigDecimal.valueOf(totalExpense) : BigDecimal.ZERO;
        
        return budget.subtract(expense);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return String.format("%,d", amount.intValue());
    }
}
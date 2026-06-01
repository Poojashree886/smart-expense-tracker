package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.Category;
import com.expensetrackers.smart_expense_trackers.entity.Transaction;
import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.CategoryRepository;
import com.expensetrackers.smart_expense_trackers.repository.TransactionRepository;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/reports")
public class ReportsController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    // Get current logged in user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    @GetMapping
    public String reportsPage(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Model model) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        // Set default month/year to current if not provided
        LocalDate now = LocalDate.now();
        int selectedMonth = month != null ? month : now.getMonthValue();
        int selectedYear = year != null ? year : now.getYear();

        // Month names for dropdown
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        // Generate years for dropdown (current year - 2 to current year)
        List<Integer> years = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear; y++) {
            years.add(y);
        }

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("years", years);

        return "reports/index";
    }

    // API endpoint for monthly summary data (for charts)
    @GetMapping("/api/monthly-summary")
    @ResponseBody
    public Map<String, Object> getMonthlySummary(
            @RequestParam int month,
            @RequestParam int year) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return Collections.emptyMap();

        Map<String, Object> response = new HashMap<>();

        // Get all transactions for the month
        List<Transaction> transactions = transactionRepository.findByUserIdAndMonthAndYear(
                currentUser.getId(), month, year);

        // Calculate totals
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> categorySpending = new HashMap<>();

        for (Transaction t : transactions) {
            if ("INCOME".equals(t.getType())) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
                String categoryName = t.getCategory().getName();
                categorySpending.put(categoryName,
                        categorySpending.getOrDefault(categoryName, BigDecimal.ZERO)
                                .add(t.getAmount()));
            }
        }

        BigDecimal savings = totalIncome.subtract(totalExpense);
        BigDecimal savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
                ? savings.multiply(new BigDecimal(100)).divide(totalIncome, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        response.put("totalIncome", totalIncome);
        response.put("totalExpense", totalExpense);
        response.put("savings", savings);
        response.put("savingsRate", savingsRate);
        response.put("transactionCount", transactions.size());
        response.put("categorySpending", categorySpending);

        return response;
    }

    // API endpoint for trend data (last 6 months)
    @GetMapping("/api/trends")
    @ResponseBody
    public Map<String, Object> getTrends(@RequestParam int year) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return Collections.emptyMap();

        Map<String, Object> response = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<BigDecimal> incomeData = new ArrayList<>();
        List<BigDecimal> expenseData = new ArrayList<>();
        List<BigDecimal> savingsData = new ArrayList<>();

        // Get last 6 months including current
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate date = now.minusMonths(i);
            int month = date.getMonthValue();
            int yearValue = date.getYear();

            // Only include if within requested year range
            if (yearValue < year - 1 || yearValue > year) continue;

            String monthName = date.getMonth().toString().substring(0, 3) + " " + yearValue;
            labels.add(monthName);

            Double income = transactionRepository.getTotalIncomeByMonth(currentUser.getId(), month, yearValue);
            Double expense = transactionRepository.getTotalExpenseByMonth(currentUser.getId(), month, yearValue);

            BigDecimal incomeVal = income != null ? BigDecimal.valueOf(income) : BigDecimal.ZERO;
            BigDecimal expenseVal = expense != null ? BigDecimal.valueOf(expense) : BigDecimal.ZERO;

            incomeData.add(incomeVal);
            expenseData.add(expenseVal);
            savingsData.add(incomeVal.subtract(expenseVal));
        }

        response.put("labels", labels);
        response.put("income", incomeData);
        response.put("expense", expenseData);
        response.put("savings", savingsData);

        return response;
    }

    // API endpoint for yearly summary
    @GetMapping("/api/yearly-summary")
    @ResponseBody
    public Map<String, Object> getYearlySummary(@RequestParam int year) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return Collections.emptyMap();

        Map<String, Object> response = new HashMap<>();
        BigDecimal totalYearlyIncome = BigDecimal.ZERO;
        BigDecimal totalYearlyExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> yearlyCategorySpending = new HashMap<>();

        // Get all expense categories
        List<Category> categories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());

        for (int month = 1; month <= 12; month++) {
            Double income = transactionRepository.getTotalIncomeByMonth(currentUser.getId(), month, year);
            Double expense = transactionRepository.getTotalExpenseByMonth(currentUser.getId(), month, year);

            if (income != null) totalYearlyIncome = totalYearlyIncome.add(BigDecimal.valueOf(income));
            if (expense != null) totalYearlyExpense = totalYearlyExpense.add(BigDecimal.valueOf(expense));
        }

        // Calculate category totals for the year
        for (Category category : categories) {
            Double categoryTotal = 0.0;
            for (int month = 1; month <= 12; month++) {
                Double spent = transactionRepository.getTotalExpenseByCategoryAndMonth(
                        currentUser.getId(), category.getId(), month, year);
                if (spent != null) {
                    categoryTotal += spent;
                }
            }
            if (categoryTotal > 0) {
                yearlyCategorySpending.put(category.getName(), BigDecimal.valueOf(categoryTotal));
            }
        }

        BigDecimal yearlySavings = totalYearlyIncome.subtract(totalYearlyExpense);
        BigDecimal savingsRate = totalYearlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? yearlySavings.multiply(new BigDecimal(100)).divide(totalYearlyIncome, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        response.put("totalIncome", totalYearlyIncome);
        response.put("totalExpense", totalYearlyExpense);
        response.put("savings", yearlySavings);
        response.put("savingsRate", savingsRate);
        response.put("categorySpending", yearlyCategorySpending);

        return response;
    }

    // API endpoint for comparison (this year vs last year)
    @GetMapping("/api/comparison")
    @ResponseBody
    public Map<String, Object> getComparison(@RequestParam int year) {

        User currentUser = getCurrentUser();
        if (currentUser == null) return Collections.emptyMap();

        Map<String, Object> response = new HashMap<>();

        int lastYear = year - 1;

        // This year totals
        Double thisYearIncome = 0.0;
        Double thisYearExpense = 0.0;
        Double lastYearIncome = 0.0;
        Double lastYearExpense = 0.0;

        for (int month = 1; month <= 12; month++) {
            Double incomeThis = transactionRepository.getTotalIncomeByMonth(currentUser.getId(), month, year);
            Double expenseThis = transactionRepository.getTotalExpenseByMonth(currentUser.getId(), month, year);
            Double incomeLast = transactionRepository.getTotalIncomeByMonth(currentUser.getId(), month, lastYear);
            Double expenseLast = transactionRepository.getTotalExpenseByMonth(currentUser.getId(), month, lastYear);

            if (incomeThis != null) thisYearIncome += incomeThis;
            if (expenseThis != null) thisYearExpense += expenseThis;
            if (incomeLast != null) lastYearIncome += incomeLast;
            if (expenseLast != null) lastYearExpense += expenseLast;
        }

        BigDecimal thisYearIncomeBD = BigDecimal.valueOf(thisYearIncome);
        BigDecimal thisYearExpenseBD = BigDecimal.valueOf(thisYearExpense);
        BigDecimal lastYearIncomeBD = BigDecimal.valueOf(lastYearIncome);
        BigDecimal lastYearExpenseBD = BigDecimal.valueOf(lastYearExpense);

        // Calculate percentage changes
        BigDecimal incomeChange = calculatePercentageChange(thisYearIncomeBD, lastYearIncomeBD);
        BigDecimal expenseChange = calculatePercentageChange(thisYearExpenseBD, lastYearExpenseBD);

        response.put("thisYearIncome", thisYearIncomeBD);
        response.put("thisYearExpense", thisYearExpenseBD);
        response.put("lastYearIncome", lastYearIncomeBD);
        response.put("lastYearExpense", lastYearExpenseBD);
        response.put("incomeChange", incomeChange);
        response.put("expenseChange", expenseChange);

        return response;
    }

    private BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(new BigDecimal(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }
}
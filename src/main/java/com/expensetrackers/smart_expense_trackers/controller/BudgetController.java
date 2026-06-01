package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.Budget;
import com.expensetrackers.smart_expense_trackers.entity.Category;
import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.BudgetRepository;
import com.expensetrackers.smart_expense_trackers.repository.CategoryRepository;
import com.expensetrackers.smart_expense_trackers.repository.TransactionRepository;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.security.UserDetailsImpl;
import com.expensetrackers.smart_expense_trackers.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
@RequestMapping("/budgets")
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private EmailService emailService;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    @GetMapping
    public String listBudgets(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Model model) {
        
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        LocalDate now = LocalDate.now();
        int selectedMonth = month != null ? month : now.getMonthValue();
        int selectedYear = year != null ? year : now.getYear();

        // Get all categories
        List<Category> allCategories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());
        
        // Get existing budgets for selected month/year
        List<Budget> existingBudgets = budgetRepository.findByUserIdAndMonthAndYear(
                currentUser.getId(), selectedMonth, selectedYear);
        
        // Create a map of category ID to budget
        Map<Long, Budget> budgetMap = new HashMap<>();
        for (Budget budget : existingBudgets) {
            if (budget.getCategory() != null) {
                budgetMap.put(budget.getCategory().getId(), budget);
            }
        }

        // Calculate budget progress for categories that HAVE budgets only
        List<Map<String, Object>> budgetProgress = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        // Only iterate through categories that have budgets (budgetAmount > 0)
        for (Category category : allCategories) {
            // Get budget amount if exists
            Budget budget = budgetMap.get(category.getId());
            BigDecimal budgetAmount = budget != null ? budget.getAmount() : BigDecimal.ZERO;
            
            // ONLY ADD CATEGORIES THAT HAVE BUDGETS (amount > 0)
            if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> progress = new HashMap<>();
                progress.put("categoryId", category.getId());
                progress.put("categoryName", category.getName());
                progress.put("categoryIcon", category.getIcon() != null ? category.getIcon() : "fas fa-tag");
                progress.put("budgetAmount", budgetAmount);
                
                if (budget != null) {
                    progress.put("id", budget.getId());
                }
                
                totalBudget = totalBudget.add(budgetAmount);

                // Calculate actual spending for this category
                Double spentDouble = transactionRepository.getTotalExpenseByCategoryAndMonth(
                        currentUser.getId(), category.getId(), selectedMonth, selectedYear);
                BigDecimal spentAmount = spentDouble != null ? BigDecimal.valueOf(spentDouble) : BigDecimal.ZERO;
                progress.put("spentAmount", spentAmount);
                totalSpent = totalSpent.add(spentAmount);

                // Calculate percentage
                int percentage = 0;
                if (budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
                    percentage = spentAmount.multiply(new BigDecimal(100))
                            .divide(budgetAmount, 0, RoundingMode.HALF_UP)
                            .intValue();
                }
                progress.put("percentage", percentage);

                // Determine status
                String status = "good";
                String statusColor = "success";
                if (percentage > 100) {
                    status = "exceeded";
                    statusColor = "danger";
                } else if (percentage > 80) {
                    status = "warning";
                    statusColor = "warning";
                }
                progress.put("status", status);
                progress.put("statusColor", statusColor);

                // Calculate remaining amount
                BigDecimal remaining = budgetAmount.subtract(spentAmount);
                progress.put("remaining", remaining);

                budgetProgress.add(progress);
            }
        }

        // Calculate overall remaining
        BigDecimal totalRemaining = totalBudget.subtract(totalSpent);
        
        // Calculate overall budget health
        int overallHealth = 100;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            overallHealth = totalRemaining.multiply(new BigDecimal(100))
                    .divide(totalBudget, 0, RoundingMode.HALF_UP)
                    .intValue();
            if (overallHealth < 0) overallHealth = 0;
        }

        String[] monthNames = {"January", "February", "March", "April", "May", "June", 
                               "July", "August", "September", "October", "November", "December"};

        // Generate years for dropdown
        List<Integer> years = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear + 2; y++) {
            years.add(y);
        }

        model.addAttribute("budgetProgress", budgetProgress);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("years", years);
        model.addAttribute("totalBudget", String.format("%,.0f", totalBudget));
        model.addAttribute("totalSpent", String.format("%,.0f", totalSpent));
        model.addAttribute("totalRemaining", String.format("%,.0f", totalRemaining));
        model.addAttribute("overallHealth", overallHealth);

        return "budgets/list";
    }

    @GetMapping("/set")
    public String setBudgetForm(@RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) Integer month,
                                @RequestParam(required = false) Integer year,
                                Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        LocalDate now = LocalDate.now();
        int selectedMonth = month != null ? month : now.getMonthValue();
        int selectedYear = year != null ? year : now.getYear();

        List<Category> categories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());
        
        Budget budget = new Budget();
        budget.setMonth(selectedMonth);
        budget.setYear(selectedYear);
        
        if (categoryId != null) {
            Optional<Budget> existingBudget = budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                    currentUser.getId(), categoryId, selectedMonth, selectedYear);
            if (existingBudget.isPresent()) {
                budget = existingBudget.get();
            } else {
                categories.stream()
                        .filter(c -> c.getId().equals(categoryId))
                        .findFirst()
                        .ifPresent(budget::setCategory);
            }
        }

        String[] monthNames = {"January", "February", "March", "April", "May", "June", 
                               "July", "August", "September", "October", "November", "December"};

        model.addAttribute("budget", budget);
        model.addAttribute("categories", categories);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("pageTitle", categoryId == null ? "Set New Budget" : "Edit Budget");

        return "budgets/form";
    }

    @PostMapping("/save")
    public String saveBudget(@ModelAttribute Budget budget,
                             @RequestParam Long categoryId,
                             @RequestParam int month,
                             @RequestParam int year,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            budget.setUser(currentUser);
            Category category = categoryRepository.findById(categoryId).orElse(null);
            budget.setCategory(category);
            budget.setMonth(month);
            budget.setYear(year);
            
            Optional<Budget> existingBudget = budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                    currentUser.getId(), categoryId, month, year);
            
            boolean isNewBudget = false;
            
            if (existingBudget.isPresent() && !existingBudget.get().getId().equals(budget.getId())) {
                Budget existing = existingBudget.get();
                existing.setAmount(budget.getAmount());
                budgetRepository.save(existing);
                redirectAttributes.addFlashAttribute("success", "Budget updated successfully!");
            } else {
                budgetRepository.save(budget);
                redirectAttributes.addFlashAttribute("success", "Budget saved successfully!");
                isNewBudget = true;
            }
            
            // 📧 Check if current spending already exceeds or warns about this new/updated budget
            Double spentDouble = transactionRepository.getTotalExpenseByCategoryAndMonth(
                currentUser.getId(), categoryId, month, year);
            double spentAmount = spentDouble != null ? spentDouble : 0.0;
            double budgetAmount = budget.getAmount().doubleValue();
            double percentageUsed = (spentAmount / budgetAmount) * 100;
            
            if (spentAmount > budgetAmount) {
                emailService.sendBudgetExceededAlert(currentUser, category.getName(), budgetAmount, spentAmount);
                System.out.println("📧 Budget EXCEEDED alert sent for " + category.getName());
            } else if (percentageUsed >= 80) {
                emailService.sendBudgetWarningAlert(currentUser, category.getName(), budgetAmount, spentAmount);
                System.out.println("📧 Budget WARNING alert sent for " + category.getName() + " (" + String.format("%.0f", percentageUsed) + "%)");
            } else if (isNewBudget) {
                System.out.println("✅ New budget created for " + category.getName() + ": ₹" + budgetAmount);
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving budget: " + e.getMessage());
        }
        return "redirect:/budgets?month=" + month + "&year=" + year;
    }

    @GetMapping("/delete/{id}")
    public String deleteBudget(@PathVariable Long id,
                               @RequestParam int month,
                               @RequestParam int year,
                               RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Optional<Budget> budgetOpt = budgetRepository.findById(id);
        if (budgetOpt.isPresent() && budgetOpt.get().getUser().getId().equals(currentUser.getId())) {
            String categoryName = budgetOpt.get().getCategory() != null ? budgetOpt.get().getCategory().getName() : "Unknown";
            budgetRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Budget deleted successfully!");
            System.out.println("🗑️ Budget deleted for category: " + categoryName);
        } else {
            redirectAttributes.addFlashAttribute("error", "Budget not found!");
        }

        return "redirect:/budgets?month=" + month + "&year=" + year;
    }

    @PostMapping("/copy-previous")
    public String copyPreviousMonth(@RequestParam int targetMonth,
                                    @RequestParam int targetYear,
                                    RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            int prevMonth = targetMonth - 1;
            int prevYear = targetYear;
            if (prevMonth == 0) {
                prevMonth = 12;
                prevYear = targetYear - 1;
            }

            List<Budget> previousBudgets = budgetRepository.findByUserIdAndMonthAndYear(
                    currentUser.getId(), prevMonth, prevYear);
            
            if (previousBudgets.isEmpty()) {
                redirectAttributes.addFlashAttribute("info", "No budgets found for previous month.");
                return "redirect:/budgets?month=" + targetMonth + "&year=" + targetYear;
            }

            int copied = 0;
            for (Budget prevBudget : previousBudgets) {
                Optional<Budget> existing = budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                        currentUser.getId(), prevBudget.getCategory().getId(), targetMonth, targetYear);
                
                if (existing.isEmpty()) {
                    Budget newBudget = new Budget();
                    newBudget.setUser(currentUser);
                    newBudget.setCategory(prevBudget.getCategory());
                    newBudget.setAmount(prevBudget.getAmount());
                    newBudget.setMonth(targetMonth);
                    newBudget.setYear(targetYear);
                    budgetRepository.save(newBudget);
                    copied++;
                }
            }

            String[] monthNames = {"January", "February", "March", "April", "May", "June", 
                                   "July", "August", "September", "October", "November", "December"};
            
            redirectAttributes.addFlashAttribute("success", 
                    copied + " budgets copied from " + monthNames[prevMonth-1] + " " + prevYear);
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error copying budgets: " + e.getMessage());
        }

        return "redirect:/budgets?month=" + targetMonth + "&year=" + targetYear;
    }

    @GetMapping("/find")
    @ResponseBody
    public Optional<Budget> findBudget(@RequestParam Long categoryId,
                                       @RequestParam int month,
                                       @RequestParam int year) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return Optional.empty();
        
        return budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                currentUser.getId(), categoryId, month, year);
    }
}
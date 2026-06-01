package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.Budget;
import com.expensetrackers.smart_expense_trackers.entity.Category;
import com.expensetrackers.smart_expense_trackers.entity.Transaction;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BudgetRepository budgetRepository;
    
    @Autowired
    private EmailService emailService;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    @GetMapping
    public String listTransactions(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            Model model) {
        
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        LocalDate now = LocalDate.now();
        int selectedMonth = month != null ? month : now.getMonthValue();
        int selectedYear = year != null ? year : now.getYear();

        List<Category> categories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());
        
        List<Transaction> transactions;
        if (categoryId != null) {
            transactions = transactionRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                    currentUser.getId(), categoryId, selectedMonth, selectedYear);
        } else if (type != null && !type.isEmpty()) {
            transactions = transactionRepository.findByUserIdAndTypeAndMonthAndYear(
                    currentUser.getId(), type, selectedMonth, selectedYear);
        } else {
            transactions = transactionRepository.findByUserIdAndMonthAndYear(
                    currentUser.getId(), selectedMonth, selectedYear);
        }

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        
        for (Transaction t : transactions) {
            if ("INCOME".equals(t.getType())) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }
        
        BigDecimal balance = totalIncome.subtract(totalExpense);

        String[] monthNames = {"January", "February", "March", "April", "May", "June", 
                               "July", "August", "September", "October", "November", "December"};

        model.addAttribute("transactions", transactions);
        model.addAttribute("categories", categories);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedType", type);
        model.addAttribute("totalIncome", String.format("%,.0f", totalIncome));
        model.addAttribute("totalExpense", String.format("%,.0f", totalExpense));
        model.addAttribute("balance", String.format("%,.0f", balance));
        model.addAttribute("monthNames", monthNames);

        return "transactions/list";
    }

    @GetMapping("/add")
    public String addTransactionForm(@RequestParam(required = false) String type, Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        List<Category> categories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());
        
        Transaction transaction = new Transaction();
        transaction.setDate(LocalDate.now());
        if (type != null && (type.equals("INCOME") || type.equals("EXPENSE"))) {
            transaction.setType(type);
        }

        model.addAttribute("transaction", transaction);
        model.addAttribute("categories", categories);
        model.addAttribute("pageTitle", "Add Transaction");
        
        return "transactions/form";
    }

    @GetMapping("/edit/{id}")
    public String editTransactionForm(@PathVariable Long id, Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Optional<Transaction> transactionOpt = transactionRepository.findById(id);
        if (transactionOpt.isEmpty() || !transactionOpt.get().getUser().getId().equals(currentUser.getId())) {
            return "redirect:/transactions?error=notfound";
        }

        List<Category> categories = categoryRepository.findByUserIdOrIsDefaultTrue(currentUser.getId());

        model.addAttribute("transaction", transactionOpt.get());
        model.addAttribute("categories", categories);
        model.addAttribute("pageTitle", "Edit Transaction");
        
        return "transactions/form";
    }

    @PostMapping("/save")
    public String saveTransaction(@ModelAttribute Transaction transaction,
                                  @RequestParam Long categoryId,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            if (transaction.getId() != null) {
                // Editing existing transaction
                Optional<Transaction> existingOpt = transactionRepository.findById(transaction.getId());
                if (existingOpt.isPresent() && existingOpt.get().getUser().getId().equals(currentUser.getId())) {
                    Transaction existing = existingOpt.get();
                    
                    // Get old category for budget calculation
                    Category oldCategory = existing.getCategory();
                    double oldAmount = existing.getAmount().doubleValue();
                    boolean wasExpense = "EXPENSE".equals(existing.getType());
                    
                    existing.setType(transaction.getType());
                    existing.setAmount(transaction.getAmount());
                    existing.setDate(transaction.getDate());
                    existing.setNote(transaction.getNote());
                    Category category = categoryRepository.findById(categoryId).orElse(null);
                    existing.setCategory(category);
                    transactionRepository.save(existing);
                    
                    redirectAttributes.addFlashAttribute("success", "Transaction updated successfully!");
                    
                    // 📧 Send email notification for update if expense
                    if ("EXPENSE".equals(transaction.getType())) {
                        checkAndSendBudgetAlert(currentUser, category, transaction.getDate());
                    }
                } else {
                    redirectAttributes.addFlashAttribute("error", "Transaction not found!");
                }
            } else {
                // New transaction
                transaction.setUser(currentUser);
                Category category = categoryRepository.findById(categoryId).orElse(null);
                transaction.setCategory(category);
                transactionRepository.save(transaction);
                
                redirectAttributes.addFlashAttribute("success", "Transaction saved successfully!");
                
                // 📧 SEND REAL-TIME EMAIL ALERTS 📧
                
                // 1. Send transaction confirmation email
                emailService.sendTransactionConfirmation(
                    currentUser,
                    category.getName(),
                    transaction.getType(),
                    transaction.getAmount().doubleValue(),
                    transaction.getDate().toString()
                );
                System.out.println("📧 Transaction confirmation email sent to: " + currentUser.getEmail());
                
                // 2. Check and send budget alerts if this is an expense
                if ("EXPENSE".equals(transaction.getType())) {
                    checkAndSendBudgetAlert(currentUser, category, transaction.getDate());
                }
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving transaction: " + e.getMessage());
        }
        return "redirect:/transactions";
    }

    @GetMapping("/delete/{id}")
    public String deleteTransaction(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(id);
            if (transactionOpt.isPresent() && transactionOpt.get().getUser().getId().equals(currentUser.getId())) {
                transactionRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("success", "Transaction deleted successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Transaction not found!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting transaction: " + e.getMessage());
        }
        return "redirect:/transactions";
    }

    @GetMapping("/by-month")
    @ResponseBody
    public List<Transaction> getTransactionsByMonth(@RequestParam int month, @RequestParam int year) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return List.of();
        
        return transactionRepository.findByUserIdAndMonthAndYear(currentUser.getId(), month, year);
    }
    
    // Helper method to check budget and send alerts
    private void checkAndSendBudgetAlert(User user, Category category, LocalDate transactionDate) {
        try {
            int month = transactionDate.getMonthValue();
            int year = transactionDate.getYear();
            
            // Get budget for this category in this month
            Optional<Budget> budgetOpt = budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                user.getId(), category.getId(), month, year);
            
            if (budgetOpt.isPresent()) {
                Budget budget = budgetOpt.get();
                double budgetAmount = budget.getAmount().doubleValue();
                
                // Calculate total spent for this category this month
                Double spentDouble = transactionRepository.getTotalExpenseByCategoryAndMonth(
                    user.getId(), category.getId(), month, year);
                double spentAmount = spentDouble != null ? spentDouble : 0.0;
                
                double percentageUsed = (spentAmount / budgetAmount) * 100;
                
                // Check if budget exceeded
                if (spentAmount > budgetAmount) {
                    emailService.sendBudgetExceededAlert(user, category.getName(), budgetAmount, spentAmount);
                    System.out.println("📧 Budget EXCEEDED alert sent for " + category.getName());
                }
                // Check if budget warning threshold reached (80% or more)
                else if (percentageUsed >= 80) {
                    emailService.sendBudgetWarningAlert(user, category.getName(), budgetAmount, spentAmount);
                    System.out.println("📧 Budget WARNING alert sent for " + category.getName() + " (" + String.format("%.0f", percentageUsed) + "%)");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to check budget alert: " + e.getMessage());
        }
    }
}
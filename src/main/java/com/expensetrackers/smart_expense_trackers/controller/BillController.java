package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.Bill;
import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.BillRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/bills")
public class BillController {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EmailService emailService;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    @GetMapping
    public String listBills(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        List<Bill> bills = billRepository.findByUserIdOrderByDueDateAsc(currentUser.getId());
        List<Bill> unpaidBills = billRepository.findByUserIdAndIsPaidFalseOrderByDueDateAsc(currentUser.getId());
        
        LocalDate today = LocalDate.now();
        List<Bill> overdueBills = billRepository.findOverdueBills(currentUser.getId(), today);
        
        double totalMonthlyBills = bills.stream()
                .filter(b -> b.getDueDate().getMonth() == today.getMonth())
                .mapToDouble(Bill::getAmount)
                .sum();

        model.addAttribute("bills", bills);
        model.addAttribute("unpaidBills", unpaidBills);
        model.addAttribute("overdueBills", overdueBills);
        model.addAttribute("totalMonthlyBills", totalMonthlyBills);
        model.addAttribute("pageTitle", "Bills Manager");

        return "bills/list";
    }

    @GetMapping("/add")
    public String addBillForm(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Bill bill = new Bill();
        bill.setDueDate(LocalDate.now().plusDays(30));
        bill.setIsPaid(false);
        bill.setIsRecurring(false);

        model.addAttribute("bill", bill);
        model.addAttribute("pageTitle", "Add New Bill");
        
        return "bills/form";
    }

    @GetMapping("/edit/{id}")
    public String editBillForm(@PathVariable Long id, Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Optional<Bill> billOpt = billRepository.findById(id);
        if (billOpt.isEmpty() || !billOpt.get().getUser().getId().equals(currentUser.getId())) {
            return "redirect:/bills?error=notfound";
        }

        model.addAttribute("bill", billOpt.get());
        model.addAttribute("pageTitle", "Edit Bill");
        
        return "bills/form";
    }

    @PostMapping("/save")
    public String saveBill(@ModelAttribute Bill bill,
                           RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            if (bill.getId() != null) {
                Optional<Bill> existingOpt = billRepository.findById(bill.getId());
                if (existingOpt.isPresent() && existingOpt.get().getUser().getId().equals(currentUser.getId())) {
                    Bill existing = existingOpt.get();
                    
                    // Check if due date changed to send new reminder
                    boolean dueDateChanged = !existing.getDueDate().equals(bill.getDueDate());
                    
                    existing.setBillName(bill.getBillName());
                    existing.setAmount(bill.getAmount());
                    existing.setDueDate(bill.getDueDate());
                    existing.setCategory(bill.getCategory());
                    existing.setIsRecurring(bill.getIsRecurring());
                    existing.setRecurringPeriod(bill.getRecurringPeriod());
                    existing.setNotes(bill.getNotes());
                    existing.setUpdatedAt(LocalDate.now().atStartOfDay());
                    billRepository.save(existing);
                    redirectAttributes.addFlashAttribute("success", "Bill updated successfully!");
                    
                    // Send reminder if due date changed and is within 3 days
                    if (dueDateChanged) {
                        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), bill.getDueDate());
                        if (daysUntilDue <= 3 && daysUntilDue > 0 && !bill.getIsPaid()) {
                            emailService.sendBillReminder(currentUser, bill.getBillName(), bill.getAmount(), (int)daysUntilDue);
                            System.out.println("📧 Bill reminder sent for updated bill: " + bill.getBillName());
                        }
                    }
                }
            } else {
                bill.setUser(currentUser);
                bill.setCreatedAt(LocalDate.now().atStartOfDay());
                bill.setUpdatedAt(LocalDate.now().atStartOfDay());
                billRepository.save(bill);
                redirectAttributes.addFlashAttribute("success", "Bill added successfully!");
                
                // 📧 Send reminder based on due date
                long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), bill.getDueDate());
                if (daysUntilDue <= 3 && daysUntilDue > 0 && !bill.getIsPaid()) {
                    emailService.sendBillReminder(currentUser, bill.getBillName(), bill.getAmount(), (int)daysUntilDue);
                    System.out.println("📧 Bill reminder sent for: " + bill.getBillName());
                } else if (daysUntilDue < 0) {
                    System.out.println("⚠️ Bill " + bill.getBillName() + " is already overdue!");
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving bill: " + e.getMessage());
        }
        return "redirect:/bills";
    }

    @GetMapping("/mark-paid/{id}")
    public String markAsPaid(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Optional<Bill> billOpt = billRepository.findById(id);
        if (billOpt.isPresent() && billOpt.get().getUser().getId().equals(currentUser.getId())) {
            Bill bill = billOpt.get();
            bill.setIsPaid(true);
            bill.setPaidDate(LocalDate.now());
            billRepository.save(bill);
            redirectAttributes.addFlashAttribute("success", "Bill marked as paid!");
            System.out.println("✅ Bill marked as paid: " + bill.getBillName());
        } else {
            redirectAttributes.addFlashAttribute("error", "Bill not found!");
        }
        return "redirect:/bills";
    }

    @GetMapping("/delete/{id}")
    public String deleteBill(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        Optional<Bill> billOpt = billRepository.findById(id);
        if (billOpt.isPresent() && billOpt.get().getUser().getId().equals(currentUser.getId())) {
            String billName = billOpt.get().getBillName();
            billRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Bill deleted successfully!");
            System.out.println("🗑️ Bill deleted: " + billName);
        } else {
            redirectAttributes.addFlashAttribute("error", "Bill not found!");
        }
        return "redirect:/bills";
    }

    @GetMapping("/upcoming")
    @ResponseBody
    public List<Bill> getUpcomingBills(@RequestParam(required = false) Integer days) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return List.of();

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days != null ? days : 30);
        
        return billRepository.findBillsDueBetween(currentUser.getId(), today, endDate);
    }
}
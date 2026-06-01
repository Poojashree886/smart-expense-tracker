package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import com.expensetrackers.smart_expense_trackers.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Get current logged in user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(null);
    }

    // 1. VIEW PROFILE PAGE - UPDATED to profile/profile
    @GetMapping
    public String viewProfile(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("user", currentUser);
        model.addAttribute("pageTitle", "My Profile");

        return "profile/profile";  // CHANGED FROM profile/view TO profile/profile
    }

    // 2. EDIT PROFILE FORM
    @GetMapping("/edit")
    public String editProfileForm(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("user", currentUser);
        model.addAttribute("pageTitle", "Edit Profile");

        return "profile/edit";
    }

    // 3. UPDATE PROFILE
    @PostMapping("/update")
    public String updateProfile(@ModelAttribute User updatedUser,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            // Update only allowed fields
            currentUser.setFullName(updatedUser.getFullName());
            currentUser.setCurrency(updatedUser.getCurrency());
            
            // Check if email is being changed
            if (!currentUser.getEmail().equals(updatedUser.getEmail())) {
                // Check if new email is already taken
                Optional<User> existingUser = userRepository.findByEmail(updatedUser.getEmail());
                if (existingUser.isPresent()) {
                    redirectAttributes.addFlashAttribute("error", "Email already in use!");
                    return "redirect:/profile/edit";
                }
                currentUser.setEmail(updatedUser.getEmail());
            }

            userRepository.save(currentUser);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating profile: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    // 4. CHANGE PASSWORD FORM
    @GetMapping("/change-password")
    public String changePasswordForm(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("pageTitle", "Change Password");
        return "profile/change-password";
    }

    // 5. UPDATE PASSWORD
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            // Check if current password is correct
            if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Current password is incorrect!");
                return "redirect:/profile/change-password";
            }

            // Check if new password and confirm password match
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "New password and confirm password do not match!");
                return "redirect:/profile/change-password";
            }

            // Check password strength
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long!");
                return "redirect:/profile/change-password";
            }

            // Update password
            currentUser.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(currentUser);

            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error changing password: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    // 6. DELETE ACCOUNT
    @PostMapping("/delete")
    public String deleteAccount(@RequestParam String password,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return "redirect:/login";

        try {
            // Verify password
            if (!passwordEncoder.matches(password, currentUser.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Password is incorrect!");
                return "redirect:/profile";
            }

            // Delete user account
            userRepository.delete(currentUser);
            
            // Logout
            SecurityContextHolder.clearContext();
            
            return "redirect:/login?accountDeleted";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting account: " + e.getMessage());
            return "redirect:/profile";
        }
    }
}
package com.expensetrackers.smart_expense_trackers.controller;

import com.expensetrackers.smart_expense_trackers.entity.User;
import com.expensetrackers.smart_expense_trackers.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String fullName,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String currency,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Check if user already exists
            if (userRepository.findByEmail(email).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Email already registered!");
                return "redirect:/register";
            }

            if (userRepository.findByUsername(username).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Username already taken!");
                return "redirect:/register";
            }

            // Create new user
            User user = new User();
            user.setFullName(fullName);
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setCurrency(currency);

            userRepository.save(user);
            
            redirectAttributes.addFlashAttribute("success", "Registration successful! Please login.");
            return "redirect:/login";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Registration failed: " + e.getMessage());
            return "redirect:/register";
        }
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,  // This can be either username or email
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Try to find user by username first
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            // If not found by username, try email
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(username);
            }
            
            // If user found and password matches
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                if (passwordEncoder.matches(password, user.getPassword())) {
                    // Store user in session
                    session.setAttribute("userId", user.getId());
                    session.setAttribute("username", user.getUsername());
                    session.setAttribute("userFullName", user.getFullName());
                    
                    return "redirect:/dashboard";
                } else {
                    redirectAttributes.addFlashAttribute("error", "Invalid password!");
                    return "redirect:/login";
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "User not found with this username/email!");
                return "redirect:/login";
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Login failed: " + e.getMessage());
            return "redirect:/login";
        }
    }
}
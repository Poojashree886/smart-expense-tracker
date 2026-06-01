package com.expensetrackers.smart_expense_trackers.config;

import com.expensetrackers.smart_expense_trackers.entity.Category;
import com.expensetrackers.smart_expense_trackers.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if categories already exist
        if (categoryRepository.count() == 0) {
            // Income Categories
            createCategory("Salary", "fas fa-money-bill-wave", true);
            createCategory("Freelance", "fas fa-laptop", true);
            createCategory("Business", "fas fa-briefcase", true);
            createCategory("Investment", "fas fa-chart-line", true);
            createCategory("Rental", "fas fa-home", true);
            createCategory("Bonus", "fas fa-gift", true);
            createCategory("Refund", "fas fa-undo-alt", true);
            createCategory("Other Income", "fas fa-plus-circle", true);
            
            // Expense Categories - Daily Life
            createCategory("Food & Dining", "fas fa-utensils", true);
            createCategory("Groceries", "fas fa-shopping-basket", true);
            createCategory("Transportation", "fas fa-car", true);
            createCategory("Fuel", "fas fa-gas-pump", true);
            createCategory("Shopping", "fas fa-shopping-bag", true);
            createCategory("Entertainment", "fas fa-film", true);
            createCategory("Healthcare", "fas fa-heartbeat", true);
            createCategory("Education", "fas fa-graduation-cap", true);
            createCategory("Rent", "fas fa-home", true);
            createCategory("Utilities", "fas fa-bolt", true);
            createCategory("Internet", "fas fa-wifi", true);
            createCategory("Mobile", "fas fa-mobile-alt", true);
            createCategory("Insurance", "fas fa-shield-alt", true);
            createCategory("Subscriptions", "fas fa-calendar-check", true);
            createCategory("Personal Care", "fas fa-smile", true);
            createCategory("Clothing", "fas fa-tshirt", true);
            createCategory("Gifts", "fas fa-gift", true);
            createCategory("Travel", "fas fa-plane", true);
            createCategory("Pet Care", "fas fa-dog", true);
            createCategory("Home Maintenance", "fas fa-tools", true);
            createCategory("Taxes", "fas fa-file-invoice", true);
            createCategory("Other Expenses", "fas fa-minus-circle", true);
            
            System.out.println("✅ 25+ default categories created successfully!");
        }
    }

    private void createCategory(String name, String icon, boolean isDefault) {
        Category category = new Category();
        category.setName(name);
        category.setIcon(icon);
        category.setDefault(isDefault);
        categoryRepository.save(category);
    }
}
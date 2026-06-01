package com.expensetrackers.smart_expense_trackers.repository;

import com.expensetrackers.smart_expense_trackers.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    
    // Find all budgets for a user
    List<Budget> findByUserId(Long userId);
    
    // Find budgets by user, month, and year
    List<Budget> findByUserIdAndMonthAndYear(Long userId, int month, int year);
    
    // Find specific budget by user, category, month, and year
    Optional<Budget> findByUserIdAndCategoryIdAndMonthAndYear(
            @Param("userId") Long userId, 
            @Param("categoryId") Long categoryId, 
            @Param("month") int month, 
            @Param("year") int year);
    
    // Get total budget amount for a month
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Budget b WHERE b.user.id = :userId AND b.month = :month AND b.year = :year")
    Double getTotalBudgetByMonth(@Param("userId") Long userId, @Param("month") int month, @Param("year") int year);
}
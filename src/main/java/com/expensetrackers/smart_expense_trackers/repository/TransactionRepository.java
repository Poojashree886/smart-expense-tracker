package com.expensetrackers.smart_expense_trackers.repository;

import com.expensetrackers.smart_expense_trackers.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    // Find all transactions for a user ordered by date
    List<Transaction> findByUserIdOrderByDateDesc(Long userId);
    
    // Find transactions by month and year
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND MONTH(t.date) = :month AND YEAR(t.date) = :year ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndMonthAndYear(@Param("userId") Long userId, @Param("month") int month, @Param("year") int year);
    
    // Find transactions by category, month, and year
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.category.id = :categoryId AND MONTH(t.date) = :month AND YEAR(t.date) = :year ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndCategoryIdAndMonthAndYear(@Param("userId") Long userId, @Param("categoryId") Long categoryId, @Param("month") int month, @Param("year") int year);
    
    // Find transactions by type, month, and year
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND MONTH(t.date) = :month AND YEAR(t.date) = :year ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndTypeAndMonthAndYear(@Param("userId") Long userId, @Param("type") String type, @Param("month") int month, @Param("year") int year);
    
    // Get total income for a specific month
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'INCOME' AND MONTH(t.date) = :month AND YEAR(t.date) = :year")
    Double getTotalIncomeByMonth(@Param("userId") Long userId, @Param("month") int month, @Param("year") int year);
    
    // Get total expense for a specific month
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND MONTH(t.date) = :month AND YEAR(t.date) = :year")
    Double getTotalExpenseByMonth(@Param("userId") Long userId, @Param("month") int month, @Param("year") int year);
    
    // Get total expense for a specific category in a specific month
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.category.id = :categoryId AND t.type = 'EXPENSE' AND MONTH(t.date) = :month AND YEAR(t.date) = :year")
    Double getTotalExpenseByCategoryAndMonth(@Param("userId") Long userId, @Param("categoryId") Long categoryId, @Param("month") int month, @Param("year") int year);

    // Find transactions by specific date
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.date = :date")
    List<Transaction> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}
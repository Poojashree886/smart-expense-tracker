package com.expensetrackers.smart_expense_trackers.repository;

import com.expensetrackers.smart_expense_trackers.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByUserIdOrderByDueDateAsc(Long userId);
    
    List<Bill> findByUserIdAndIsPaidFalseOrderByDueDateAsc(Long userId);
    
    @Query("SELECT b FROM Bill b WHERE b.user.id = :userId AND b.dueDate BETWEEN :startDate AND :endDate ORDER BY b.dueDate ASC")
    List<Bill> findBillsDueBetween(@Param("userId") Long userId, 
                                    @Param("startDate") LocalDate startDate, 
                                    @Param("endDate") LocalDate endDate);
    
    @Query("SELECT b FROM Bill b WHERE b.user.id = :userId AND b.dueDate < :currentDate AND b.isPaid = false")
    List<Bill> findOverdueBills(@Param("userId") Long userId, @Param("currentDate") LocalDate currentDate);
}
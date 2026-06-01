package com.expensetrackers.smart_expense_trackers.repository;

import com.expensetrackers.smart_expense_trackers.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("SELECT c FROM Category c WHERE c.user.id = :userId OR c.isDefault = true")
    List<Category> findByUserIdOrIsDefaultTrue(@Param("userId") Long userId);
}
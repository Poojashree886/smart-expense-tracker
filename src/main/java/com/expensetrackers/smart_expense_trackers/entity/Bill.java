package com.expensetrackers.smart_expense_trackers.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bill {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String billName;
    
    @Column(nullable = false)
    private Double amount;
    
    @Column(nullable = false)
    private LocalDate dueDate;
    
    private String category; // Rent, Utilities, Subscription, etc.
    
    private Boolean isRecurring = false;
    
    private String recurringPeriod; // MONTHLY, YEARLY, QUARTERLY
    
    private Boolean isPaid = false;
    
    private LocalDate paidDate;
    
    private String notes;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
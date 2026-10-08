package com.lifeos.repository;

import com.lifeos.entity.FinanceTransaction;
import com.lifeos.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, String>,
        JpaSpecificationExecutor<FinanceTransaction> {

    Optional<FinanceTransaction> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    List<FinanceTransaction> findByUserIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDesc(String userId,
                                                                                                     LocalDate from,
                                                                                                     LocalDate to);

    @Query("""
            select t from FinanceTransaction t
            where t.userId = :userId and t.deletedAt is null
              and t.occurredOn between :from and :to
            order by t.occurredOn desc
            """)
    List<FinanceTransaction> findInRange(@Param("userId") String userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(t.amount), 0) from FinanceTransaction t
            where t.userId = :userId and t.deletedAt is null
              and t.transactionType = :type
              and t.occurredOn between :from and :to
            """)
    java.math.BigDecimal sumAmount(@Param("userId") String userId,
                                   @Param("type") TransactionType type,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(t.amount), 0) from FinanceTransaction t
            where t.userId = :userId and t.deletedAt is null and t.transactionType = :type
              and t.category = :category
              and t.occurredOn between :from and :to
            """)
    java.math.BigDecimal sumAmountByCategory(@Param("userId") String userId,
                                             @Param("type") TransactionType type,
                                             @Param("category") String category,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query("""
            select t.category as category, sum(t.amount) as total
            from FinanceTransaction t
            where t.userId = :userId and t.deletedAt is null and t.transactionType = :type
              and t.occurredOn between :from and :to
            group by t.category
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> sumByCategory(@Param("userId") String userId,
                                      @Param("type") TransactionType type,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query("select distinct t.category from FinanceTransaction t where t.userId = :userId and t.deletedAt is null order by t.category")
    List<String> findCategories(@Param("userId") String userId);

    Page<FinanceTransaction> findByUserIdAndDeletedAtIsNullOrderByOccurredOnDesc(String userId, Pageable pageable);

    interface CategoryTotal {
        String getCategory();

        java.math.BigDecimal getTotal();
    }
}
package com.healthconnect.repository;

import com.healthconnect.model.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    List<Medicine> findAllByOrderByNameAsc();

    List<Medicine> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<Medicine> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Low-stock alert. This compares two columns of the same row, which a
     * derived method name cannot express, so we write the JPQL by hand.
     */
    @Query("select m from Medicine m where m.stockQuantity <= m.reorderThreshold order by m.name")
    List<Medicine> findLowStockMedicines();
}

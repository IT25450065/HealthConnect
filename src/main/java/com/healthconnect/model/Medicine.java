package com.healthconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * UC-03 Update Medicine Stock. The pharmacy inventory.
 *
 * stockQuantity is decremented by PharmacyService when a prescription is
 * dispensed - that is the &lt;&lt;include&gt;&gt; relationship
 * "Mark Prescription as Dispensed includes Update Medicine Stock".
 */
@Entity
@Table(name = "medicine")
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicine_id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "strength", length = 60)
    private String strength;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;

    public Medicine() {
    }

    public Medicine(String name, String strength, String unit, int stockQuantity, int reorderThreshold) {
        this.name = name;
        this.strength = strength;
        this.unit = unit;
        this.stockQuantity = stockQuantity;
        this.reorderThreshold = reorderThreshold;
    }

    /** Drives the low-stock alert on the pharmacy screens. Not a database column. */
    public boolean isLowStock() {
        return stockQuantity <= reorderThreshold;
    }

    public String getLabel() {
        return strength == null || strength.isBlank() ? name : name + " " + strength;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }
}

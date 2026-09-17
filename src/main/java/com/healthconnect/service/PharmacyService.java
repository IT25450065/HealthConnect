package com.healthconnect.service;

import com.healthconnect.dto.MedicineForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.InsufficientStockException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Medicine;
import com.healthconnect.model.Prescription;
import com.healthconnect.model.PrescriptionItem;
import com.healthconnect.model.PrescriptionStatus;
import com.healthconnect.model.User;
import com.healthconnect.repository.MedicineRepository;
import com.healthconnect.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PHARMACY AND PRESCRIPTION MANAGEMENT MODULE - Ahamed M.N.N. -
 * UC-03 Update Medicine Stock / Dispense Prescription.
 *
 * dispense() is the &lt;&lt;include&gt;&gt; relationship from the use case
 * diagram made real: "Mark Prescription as Dispensed" INCLUDES "Update
 * Medicine Stock". You cannot dispense without the stock moving, because both
 * happen inside one @Transactional method.
 */
@Service
public class PharmacyService {

    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;

    public PharmacyService(PrescriptionRepository prescriptionRepository, MedicineRepository medicineRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
    }

    // ------------------------------------------------------------------
    // Prescriptions
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Prescription> findPending() {
        return prescriptionRepository.findByStatusOrderByIssuedAtAsc(PrescriptionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<Prescription> findAll() {
        return prescriptionRepository.findAllByOrderByIssuedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Prescription> findForPatient(Long patientId) {
        return prescriptionRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public Prescription getPrescription(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found (id " + id + ")"));
    }

    /**
     * UC-03 MAIN FLOW.
     *
     * Step 1: check EVERY line has enough stock before touching anything.
     *         Doing the whole check first means we never half-dispense.
     * Step 2: subtract each quantity from the medicine's stock.
     * Step 3: stamp the prescription as dispensed.
     *
     * If step 1 fails we throw, the transaction rolls back and nothing changed.
     */
    @Transactional
    public Prescription dispense(Long prescriptionId, User pharmacist, String note) {
        Prescription prescription = getPrescription(prescriptionId);

        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new BusinessException("This prescription has already been dispensed.");
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new BusinessException("This prescription was cancelled and cannot be dispensed.");
        }
        if (prescription.getItems().isEmpty()) {
            throw new BusinessException("This prescription has no medicine lines.");
        }

        // Step 1 - check the whole prescription first (UC-03 alternate flow).
        for (PrescriptionItem item : prescription.getItems()) {
            Medicine medicine = item.getMedicine();
            if (medicine.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Not enough stock of " + medicine.getLabel()
                        + ": " + item.getQuantity() + " required but only "
                        + medicine.getStockQuantity() + " in stock. Restock before dispensing.");
            }
        }

        // Step 2 - THE INCLUDED "Update Medicine Stock".
        for (PrescriptionItem item : prescription.getItems()) {
            Medicine medicine = item.getMedicine();
            medicine.setStockQuantity(medicine.getStockQuantity() - item.getQuantity());
            medicineRepository.save(medicine);
        }

        // Step 3 - record who dispensed it and when.
        prescription.setStatus(PrescriptionStatus.DISPENSED);
        prescription.setDispensedAt(LocalDateTime.now());
        prescription.setDispensedBy(pharmacist);
        prescription.setPharmacistNote(note);
        return prescriptionRepository.save(prescription);
    }

    @Transactional
    public Prescription cancelPrescription(Long prescriptionId, String reason) {
        Prescription prescription = getPrescription(prescriptionId);
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new BusinessException("A dispensed prescription cannot be cancelled.");
        }
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        prescription.setPharmacistNote(reason);
        return prescriptionRepository.save(prescription);
    }

    // ------------------------------------------------------------------
    // Medicine inventory
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Medicine> findMedicines(String search) {
        if (search == null || search.isBlank()) {
            return medicineRepository.findAllByOrderByNameAsc();
        }
        return medicineRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
    }

    /** Drives the low-stock alert banner on the pharmacy dashboard. */
    @Transactional(readOnly = true)
    public List<Medicine> findLowStock() {
        return medicineRepository.findLowStockMedicines();
    }

    @Transactional(readOnly = true)
    public Medicine getMedicine(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found (id " + id + ")"));
    }

    /** Add a new medicine or update an existing one (UC-03 "Update Medicine Stock"). */
    @Transactional
    public Medicine saveMedicine(MedicineForm form) {
        Medicine medicine;
        if (form.getId() == null) {
            if (medicineRepository.existsByNameIgnoreCase(form.getName().trim())) {
                throw new BusinessException("A medicine called \"" + form.getName().trim()
                        + "\" is already in the inventory.");
            }
            medicine = new Medicine();
        } else {
            medicine = getMedicine(form.getId());
            medicineRepository.findByNameIgnoreCase(form.getName().trim())
                    .filter(other -> !other.getId().equals(medicine.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException("Another medicine already uses the name \""
                                + form.getName().trim() + "\".");
                    });
        }

        medicine.setName(form.getName().trim());
        medicine.setStrength(form.getStrength());
        medicine.setUnit(form.getUnit());
        medicine.setStockQuantity(form.getStockQuantity());
        medicine.setReorderThreshold(form.getReorderThreshold());
        return medicineRepository.save(medicine);
    }

    /** Quick "+50 / -10" stock correction from the inventory list. */
    @Transactional
    public Medicine adjustStock(Long medicineId, int delta) {
        Medicine medicine = getMedicine(medicineId);
        int updated = medicine.getStockQuantity() + delta;
        if (updated < 0) {
            throw new BusinessException("Stock cannot go below zero. "
                    + medicine.getLabel() + " currently has " + medicine.getStockQuantity() + ".");
        }
        medicine.setStockQuantity(updated);
        return medicineRepository.save(medicine);
    }

    @Transactional(readOnly = true)
    public MedicineForm buildMedicineForm(Long id) {
        Medicine medicine = getMedicine(id);
        MedicineForm form = new MedicineForm();
        form.setId(medicine.getId());
        form.setName(medicine.getName());
        form.setStrength(medicine.getStrength());
        form.setUnit(medicine.getUnit());
        form.setStockQuantity(medicine.getStockQuantity());
        form.setReorderThreshold(medicine.getReorderThreshold());
        return form;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return prescriptionRepository.countByStatus(PrescriptionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long countDispensed() {
        return prescriptionRepository.countByStatus(PrescriptionStatus.DISPENSED);
    }

    @Transactional(readOnly = true)
    public long countTotalMedicines() {
        return medicineRepository.count();
    }

    @Transactional(readOnly = true)
    public long countOutOfStock() {
        return medicineRepository.findAll().stream()
                .filter(m -> m.getStockQuantity() <= 0)
                .count();
    }
}

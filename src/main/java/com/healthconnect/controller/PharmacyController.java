package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.dto.MedicineForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.User;
import com.healthconnect.service.AuthService;
import com.healthconnect.service.PharmacyService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * PHARMACY AND PRESCRIPTION MANAGEMENT MODULE - Ahamed M.N.N. - UC-03.
 */
@Controller
@RequestMapping("/pharmacy")
public class PharmacyController {

    private final PharmacyService pharmacyService;
    private final AuthService authService;

    public PharmacyController(PharmacyService pharmacyService, AuthService authService) {
        this.pharmacyService = pharmacyService;
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pending", pharmacyService.findPending());
        model.addAttribute("lowStock", pharmacyService.findLowStock());
        model.addAttribute("pendingCount", pharmacyService.countPending());
        model.addAttribute("dispensedCount", pharmacyService.countDispensed());
        model.addAttribute("totalMedicinesCount", pharmacyService.countTotalMedicines());
        model.addAttribute("outOfStockCount", pharmacyService.countOutOfStock());
        return "pharmacy/dashboard";
    }

    // ------------------------------------------------------------------
    // Prescriptions
    // ------------------------------------------------------------------

    @GetMapping("/prescriptions")
    public String prescriptions(@RequestParam(name = "filter", defaultValue = "pending") String filter, Model model) {
        model.addAttribute("prescriptions",
                "all".equalsIgnoreCase(filter) ? pharmacyService.findAll() : pharmacyService.findPending());
        model.addAttribute("filter", filter);
        return "pharmacy/prescriptions";
    }

    @GetMapping("/prescriptions/{id}")
    public String prescriptionDetail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("prescription", pharmacyService.getPrescription(id));
        return "pharmacy/prescription-detail";
    }

    /**
     * UC-03 main flow. One call does both halves of the
     * &lt;&lt;include&gt;&gt;: mark dispensed AND update medicine stock.
     */
    @PostMapping("/prescriptions/{id}/dispense")
    public String dispense(@PathVariable("id") Long id,
                           @RequestParam(name = "note", required = false) String note,
                           HttpSession session,
                           RedirectAttributes redirect) {
        try {
            User pharmacist = currentUser(session);
            pharmacyService.dispense(id, pharmacist, note);
            redirect.addFlashAttribute("success", "Prescription dispensed and medicine stock updated.");
            return "redirect:/pharmacy/prescriptions";
        } catch (BusinessException ex) {
            // Insufficient stock lands here - nothing was deducted.
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/pharmacy/prescriptions/" + id;
        }
    }

    @PostMapping("/prescriptions/{id}/cancel")
    public String cancelPrescription(@PathVariable("id") Long id,
                                     @RequestParam(name = "reason", required = false) String reason,
                                     RedirectAttributes redirect) {
        try {
            pharmacyService.cancelPrescription(id, reason);
            redirect.addFlashAttribute("success", "Prescription cancelled.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pharmacy/prescriptions?filter=all";
    }

    // ------------------------------------------------------------------
    // Medicine inventory
    // ------------------------------------------------------------------

    @GetMapping("/medicines")
    public String medicines(@RequestParam(name = "q", required = false) String q,
                            @RequestParam(name = "lowStockOnly", required = false, defaultValue = "false") boolean lowStockOnly,
                            Model model) {
        if (lowStockOnly) {
            model.addAttribute("medicines", pharmacyService.findLowStock());
        } else {
            model.addAttribute("medicines", pharmacyService.findMedicines(q));
        }
        model.addAttribute("lowStockCount", pharmacyService.findLowStock().size());
        model.addAttribute("q", q);
        model.addAttribute("lowStockOnly", lowStockOnly);
        return "pharmacy/medicines";
    }

    @GetMapping("/medicines/new")
    public String newMedicine(Model model) {
        MedicineForm form = new MedicineForm();
        form.setStockQuantity(0);
        form.setReorderThreshold(10);
        model.addAttribute("medicineForm", form);
        return "pharmacy/medicine-form";
    }

    @GetMapping("/medicines/{id}/edit")
    public String editMedicine(@PathVariable("id") Long id, Model model) {
        model.addAttribute("medicineForm", pharmacyService.buildMedicineForm(id));
        return "pharmacy/medicine-form";
    }

    @PostMapping("/medicines/save")
    public String saveMedicine(@Valid @ModelAttribute("medicineForm") MedicineForm medicineForm,
                               BindingResult binding,
                               Model model,
                               RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "pharmacy/medicine-form";
        }
        try {
            pharmacyService.saveMedicine(medicineForm);
            redirect.addFlashAttribute("success", "Medicine inventory updated.");
            return "redirect:/pharmacy/medicines";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "pharmacy/medicine-form";
        }
    }

    /** The "+10 / -10" buttons on the inventory list. */
    @PostMapping("/medicines/{id}/adjust")
    public String adjustStock(@PathVariable("id") Long id,
                              @RequestParam(name = "delta") int delta,
                              RedirectAttributes redirect) {
        try {
            pharmacyService.adjustStock(id, delta);
            redirect.addFlashAttribute("success", "Stock updated.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pharmacy/medicines";
    }

    private User currentUser(HttpSession session) {
        SessionUser sessionUser = AuthInterceptor.currentUser(session);
        return authService.getUser(sessionUser.getUserId());
    }
}

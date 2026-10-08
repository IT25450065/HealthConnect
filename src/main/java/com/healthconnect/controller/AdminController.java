package com.healthconnect.controller;

import com.healthconnect.dto.StaffForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.PrescriptionStatus;
import com.healthconnect.model.Role;
import com.healthconnect.model.User;
import com.healthconnect.service.AdminService;
import com.healthconnect.service.ReportService;
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
 * HOSPITAL ADMINISTRATION AND REPORTING MANAGEMENT MODULE - Vishalan S. - UC-06.
 *
 * Every URL here starts with /admin, and AuthInterceptor only lets Role.ADMIN
 * through that prefix - so there is no per-method permission code to write.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;

    public AdminController(AdminService adminService, ReportService reportService) {
        this.adminService = adminService;
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("patientCount", reportService.countPatients());
        model.addAttribute("doctorCount", reportService.countDoctors());
        model.addAttribute("staffCount", reportService.countStaff());
        model.addAttribute("appointmentCount", reportService.countAppointments());
        model.addAttribute("pendingCount", reportService.countAppointments(AppointmentStatus.PENDING));
        model.addAttribute("completedCount", reportService.countAppointments(AppointmentStatus.COMPLETED));
        model.addAttribute("videoCount", reportService.countAppointments(AppointmentType.VIDEO));
        model.addAttribute("feedbackCount", reportService.countFeedback());
        model.addAttribute("averageRating", reportService.overallAverageLabel());
        model.addAttribute("pendingPrescriptions", reportService.countPrescriptions(PrescriptionStatus.PENDING));
        model.addAttribute("lowStockCount", reportService.countLowStockMedicines());
        return "admin/dashboard";
    }

    // ------------------------------------------------------------------
    // UC-06 Manage doctor and staff accounts
    // ------------------------------------------------------------------

    @GetMapping("/staff")
    public String staffList(Model model) {
        model.addAttribute("staff", adminService.listStaff());
        return "admin/staff";
    }

    @GetMapping("/staff/new")
    public String newStaff(Model model) {
        StaffForm form = new StaffForm();
        form.setRole(Role.DOCTOR);
        model.addAttribute("staffForm", form);
        prepareStaffModel(model, true);
        return "admin/staff-form";
    }

    @GetMapping("/staff/{id}/edit")
    public String editStaff(@PathVariable("id") Long id, Model model) {
        model.addAttribute("staffForm", adminService.buildStaffForm(id));
        prepareStaffModel(model, false);
        return "admin/staff-form";
    }

    @PostMapping("/staff/save")
    public String saveStaff(@Valid @ModelAttribute("staffForm") StaffForm staffForm,
                            BindingResult binding,
                            Model model,
                            RedirectAttributes redirect) {
        boolean creating = staffForm.getUserId() == null;
        if (binding.hasErrors()) {
            prepareStaffModel(model, creating);
            return "admin/staff-form";
        }
        try {
            User saved = adminService.saveStaff(staffForm);
            redirect.addFlashAttribute("success",
                    (creating ? "Created " : "Updated ") + saved.getRole().getDisplayName()
                            + " account for " + saved.getFullName() + ".");
            return "redirect:/admin/staff";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            prepareStaffModel(model, creating);
            return "admin/staff-form";
        }
    }

    /** Deactivate or re-activate. Accounts are never deleted - history matters. */
    @PostMapping("/staff/{id}/active")
    public String setActive(@PathVariable("id") Long id,
                            @RequestParam(name = "active") boolean active,
                            RedirectAttributes redirect) {
        try {
            User user = adminService.setActive(id, active);
            redirect.addFlashAttribute("success",
                    user.getFullName() + (active ? " re-activated." : " deactivated."));
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @GetMapping("/patients")
    public String patients(Model model) {
        model.addAttribute("patients", adminService.listPatients());
        return "admin/patients";
    }

    private void prepareStaffModel(Model model, boolean creating) {
        model.addAttribute("roles", AdminService.STAFF_ROLES);
        model.addAttribute("allDays", adminService.allDays());
        model.addAttribute("creating", creating);
    }

    // ------------------------------------------------------------------
    // UC-06 Reporting
    // ------------------------------------------------------------------

    @GetMapping("/reports/appointments")
    public String appointmentReport(Model model) {
        model.addAttribute("rows", reportService.appointmentsByDoctor());
        model.addAttribute("total", reportService.countAppointments());
        model.addAttribute("pending", reportService.countAppointments(AppointmentStatus.PENDING));
        model.addAttribute("confirmed", reportService.countAppointments(AppointmentStatus.CONFIRMED));
        model.addAttribute("completed", reportService.countAppointments(AppointmentStatus.COMPLETED));
        model.addAttribute("cancelled", reportService.countAppointments(AppointmentStatus.CANCELLED));
        model.addAttribute("inPerson", reportService.countAppointments(AppointmentType.IN_PERSON));
        model.addAttribute("video", reportService.countAppointments(AppointmentType.VIDEO));
        return "admin/report-appointments";
    }

    @GetMapping("/reports/feedback")
    public String feedbackReport(Model model) {
        model.addAttribute("rows", reportService.ratingsByDoctor());
        model.addAttribute("totalFeedback", reportService.countFeedback());
        model.addAttribute("overallAverage", reportService.overallAverageLabel());
        return "admin/report-feedback";
    }
}

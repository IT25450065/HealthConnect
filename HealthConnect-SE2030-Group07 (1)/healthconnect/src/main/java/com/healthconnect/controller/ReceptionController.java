package com.healthconnect.controller;

import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.service.AdminService;
import com.healthconnect.service.AppointmentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * APPOINTMENT MANAGEMENT MODULE - Rathnayaka R.M.C.R.B. - staff side of UC-01.
 *
 * The receptionist (and the administrator) sees every appointment and can
 * confirm or cancel it. Exactly the same AppointmentService is used as on the
 * patient screens - the rules cannot drift apart between the two.
 */
@Controller
@RequestMapping("/reception")
public class ReceptionController {

    private final AppointmentService appointmentService;
    private final AdminService adminService;

    public ReceptionController(AppointmentService appointmentService, AdminService adminService) {
        this.appointmentService = appointmentService;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("today", appointmentService.findToday());
        model.addAttribute("pendingCount", appointmentService.findByStatus(AppointmentStatus.PENDING).size());
        model.addAttribute("confirmedCount", appointmentService.findByStatus(AppointmentStatus.CONFIRMED).size());
        return "reception/dashboard";
    }

    /** status is optional: no value means "show everything". */
    @GetMapping("/appointments")
    public String appointments(@RequestParam(name = "status", required = false) AppointmentStatus status, Model model) {
        model.addAttribute("appointments",
                status == null ? appointmentService.findAll() : appointmentService.findByStatus(status));
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("selectedStatus", status);
        return "reception/appointments";
    }

    @PostMapping("/appointments/{id}/confirm")
    public String confirm(@PathVariable("id") Long id, RedirectAttributes redirect) {
        try {
            appointmentService.confirmAppointment(id);
            redirect.addFlashAttribute("success", "Appointment confirmed.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reception/appointments";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancel(@PathVariable("id") Long id, RedirectAttributes redirect) {
        try {
            // null = staff caller, so the "own appointments only" rule is skipped.
            appointmentService.cancelAppointment(id, null);
            redirect.addFlashAttribute("success", "Appointment cancelled.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reception/appointments";
    }

    @GetMapping("/patients")
    public String patients(Model model) {
        model.addAttribute("patients", adminService.listPatients());
        return "reception/patients";
    }
}

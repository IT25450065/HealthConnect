package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.dto.AppointmentForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.model.VideoSession;
import com.healthconnect.service.AppointmentService;
import com.healthconnect.service.AuthService;
import com.healthconnect.service.TelemedicineService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TELEMEDICINE (ONLINE CONSULTATION) MODULE - Adithya J.M.O. - UC-04.
 *
 * The patient screens live under /patient/video and the doctor screens under
 * /doctor/video, so the existing role rules in AuthInterceptor already protect
 * both without a new rule being added.
 */
@Controller
public class TelemedicineController {

    private final TelemedicineService telemedicineService;
    private final AppointmentService appointmentService;
    private final AuthService authService;

    public TelemedicineController(TelemedicineService telemedicineService,
                                  AppointmentService appointmentService,
                                  AuthService authService) {
        this.telemedicineService = telemedicineService;
        this.appointmentService = appointmentService;
        this.authService = authService;
    }

    // ---------------------------- patient ----------------------------

    @GetMapping("/patient/video")
    public String mySessions(HttpSession session, Model model) {
        Patient patient = currentPatient(session);
        model.addAttribute("sessions", telemedicineService.findForPatient(patient));
        return "patient/video-sessions";
    }

    @GetMapping("/patient/video/book")
    public String bookForm(@RequestParam(name = "doctorId", required = false) Long doctorId,
                           @RequestParam(name = "date", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           Model model) {
        AppointmentForm form = new AppointmentForm();
        form.setDoctorId(doctorId);
        form.setDate(date != null ? date : LocalDate.now().plusDays(1));
        form.setType(AppointmentType.VIDEO);

        model.addAttribute("appointmentForm", form);
        prepareBookingModel(model, form);
        return "patient/book-video";
    }

    @PostMapping("/patient/video/book")
    public String book(@Valid @ModelAttribute("appointmentForm") AppointmentForm appointmentForm,
                       BindingResult binding,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            prepareBookingModel(model, appointmentForm);
            return "patient/book-video";
        }
        try {
            Patient patient = currentPatient(session);
            VideoSession videoSession = telemedicineService.bookVideoConsultation(patient, appointmentForm);
            redirect.addFlashAttribute("success", "Online consultation booked for "
                    + videoSession.getAppointment().getDateTimeLabel()
                    + ". Your meeting link is shown below.");
            return "redirect:/patient/video";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            prepareBookingModel(model, appointmentForm);
            return "patient/book-video";
        }
    }

    private void prepareBookingModel(Model model, AppointmentForm form) {
        List<Doctor> doctors = appointmentService.searchDoctors(null);
        model.addAttribute("doctors", doctors);

        List<String> slots = new ArrayList<>();
        Doctor selected = null;
        if (form.getDoctorId() != null) {
            selected = appointmentService.getDoctor(form.getDoctorId());
            slots = appointmentService.findFreeSlots(selected, form.getDate());
        }
        model.addAttribute("selectedDoctor", selected);
        model.addAttribute("slots", slots);
        model.addAttribute("minDate", LocalDate.now());
        model.addAttribute("maxDate", LocalDate.now().plusDays(AppointmentService.MAX_DAYS_AHEAD));
    }

    // ---------------------------- doctor ----------------------------

    @GetMapping("/doctor/video")
    public String doctorSessions(HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        model.addAttribute("sessions", telemedicineService.findForDoctor(doctor));
        return "doctor/video-sessions";
    }

    @PostMapping("/doctor/video/{id}/start")
    public String start(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirect) {
        try {
            telemedicineService.startSession(id, currentDoctor(session));
            redirect.addFlashAttribute("success", "Online session marked as in progress.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/doctor/video";
    }

    @PostMapping("/doctor/video/{id}/complete")
    public String complete(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirect) {
        try {
            telemedicineService.completeSession(id, currentDoctor(session));
            redirect.addFlashAttribute("success", "Online session marked as completed.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/doctor/video";
    }

    private Patient currentPatient(HttpSession session) {
        SessionUser sessionUser = AuthInterceptor.currentUser(session);
        return authService.requirePatient(sessionUser);
    }

    private Doctor currentDoctor(HttpSession session) {
        SessionUser sessionUser = AuthInterceptor.currentUser(session);
        return authService.requireDoctor(sessionUser);
    }
}

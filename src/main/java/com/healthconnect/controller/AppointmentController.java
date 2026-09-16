package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.dto.AppointmentForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Consultation;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.service.AppointmentService;
import com.healthconnect.service.AuthService;
import com.healthconnect.service.ConsultationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * APPOINTMENT MANAGEMENT MODULE - Rathnayaka R.M.C.R.B. - UC-01 (patient side).
 *
 * Notice how thin every method is. The controller's whole job is:
 *   read the form  ->  call the service  ->  choose the next page.
 * Not one booking rule is written here; they all live in AppointmentService.
 */
@Controller
@RequestMapping("/patient")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AuthService authService;
    private final ConsultationService consultationService;

    public AppointmentController(AppointmentService appointmentService,
                                 AuthService authService,
                                 ConsultationService consultationService) {
        this.appointmentService = appointmentService;
        this.authService = authService;
        this.consultationService = consultationService;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Patient patient = currentPatient(session);
        List<Appointment> all = appointmentService.findForPatient(patient);

        List<Appointment> upcoming = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int completed = 0;
        for (Appointment appointment : all) {
            if (appointment.isCancellable() && appointment.getAppointmentDateTime().isAfter(now)) {
                upcoming.add(appointment);
            }
            if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
                completed++;
            }
        }
        // findForPatient returns newest first; the soonest visit should be on top here.
        Collections.reverse(upcoming);

        model.addAttribute("patient", patient);
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("totalAppointments", all.size());
        model.addAttribute("completedCount", completed);
        return "patient/dashboard";
    }

    // ------------------------------------------------------------------
    // Search for a doctor
    // ------------------------------------------------------------------

    @GetMapping("/doctors")
    public String doctors(@RequestParam(name = "specialization", required = false) String specialization, Model model) {
        model.addAttribute("doctors", appointmentService.searchDoctors(specialization));
        model.addAttribute("specializations", appointmentService.listSpecializations());
        model.addAttribute("selectedSpecialization", specialization);
        return "patient/doctors";
    }

    // ------------------------------------------------------------------
    // UC-01 Book Appointment
    // ------------------------------------------------------------------

    /**
     * Shows the booking form. doctorId and date are optional: picking a doctor
     * or changing the date re-submits this same GET so the free-slot dropdown
     * can be rebuilt for that doctor and day.
     */
    @GetMapping("/appointments/book")
    public String bookForm(@RequestParam(name = "doctorId", required = false) Long doctorId,
                           @RequestParam(name = "date", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           Model model) {
        AppointmentForm form = new AppointmentForm();
        form.setDoctorId(doctorId);
        form.setDate(date != null ? date : LocalDate.now().plusDays(1));
        form.setType(AppointmentType.IN_PERSON);

        model.addAttribute("appointmentForm", form);
        prepareBookingModel(model, form);
        return "patient/book-appointment";
    }

    @PostMapping("/appointments/book")
    public String book(@Valid @ModelAttribute("appointmentForm") AppointmentForm appointmentForm,
                       BindingResult binding,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            prepareBookingModel(model, appointmentForm);
            return "patient/book-appointment";
        }
        try {
            Patient patient = currentPatient(session);
            Appointment appointment = appointmentService.bookAppointment(patient, appointmentForm);
            redirect.addFlashAttribute("success", "Appointment booked with "
                    + appointment.getDoctor().getFullName() + " on " + appointment.getDateTimeLabel() + ".");
            return "redirect:/patient/appointments";
        } catch (BusinessException ex) {
            // This is how a UC-01 extension flow (conflict, past date, outside
            // working hours) reaches the user: as a red message on the form.
            model.addAttribute("error", ex.getMessage());
            prepareBookingModel(model, appointmentForm);
            return "patient/book-appointment";
        }
    }

    /** Everything the booking page needs besides the form itself. */
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

    // ------------------------------------------------------------------
    // My appointments
    // ------------------------------------------------------------------

    @GetMapping("/appointments")
    public String myAppointments(HttpSession session, Model model) {
        Patient patient = currentPatient(session);
        model.addAttribute("appointments", appointmentService.findForPatient(patient));
        return "patient/appointments";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancel(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirect) {
        Patient patient = currentPatient(session);
        try {
            // Passing the patient id makes the service refuse to cancel
            // somebody else's appointment, even if the id is typed by hand.
            appointmentService.cancelAppointment(id, patient.getId());
            redirect.addFlashAttribute("success", "Appointment cancelled.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/patient/appointments";
    }

    // ------------------------------------------------------------------
    // My medical records (read-only view of the same history the doctor sees)
    // ------------------------------------------------------------------

    @GetMapping("/records")
    public String myRecords(HttpSession session, Model model) {
        Patient patient = currentPatient(session);
        List<Consultation> history = consultationService.viewPatientHistory(patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("history", history);
        return "patient/records";
    }

    private Patient currentPatient(HttpSession session) {
        SessionUser sessionUser = AuthInterceptor.currentUser(session);
        return authService.requirePatient(sessionUser);
    }
}

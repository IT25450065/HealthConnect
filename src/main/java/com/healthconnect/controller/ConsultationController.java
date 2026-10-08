package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.dto.ConsultationForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.Consultation;
import com.healthconnect.model.Doctor;
import com.healthconnect.service.AppointmentService;
import com.healthconnect.service.AuthService;
import com.healthconnect.service.ConsultationService;
import com.healthconnect.service.FeedbackService;
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
 * DOCTOR CONSULTATION MANAGEMENT MODULE - Gunathilake H.R.N.V. - UC-02.
 */
@Controller
@RequestMapping("/doctor")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final AppointmentService appointmentService;
    private final PharmacyService pharmacyService;
    private final FeedbackService feedbackService;
    private final AuthService authService;

    public ConsultationController(ConsultationService consultationService,
                                  AppointmentService appointmentService,
                                  PharmacyService pharmacyService,
                                  FeedbackService feedbackService,
                                  AuthService authService) {
        this.consultationService = consultationService;
        this.appointmentService = appointmentService;
        this.pharmacyService = pharmacyService;
        this.feedbackService = feedbackService;
        this.authService = authService;
    }

    /** The doctor's patient queue - UC-02 step 1. */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        model.addAttribute("doctor", doctor);
        model.addAttribute("queue", appointmentService.findQueueForDoctor(doctor));
        model.addAttribute("consultationCount", consultationService.findByDoctor(doctor).size());
        model.addAttribute("averageRating", feedbackService.averageRatingFor(doctor));
        model.addAttribute("feedbackCount", feedbackService.countFor(doctor));
        return "doctor/dashboard";
    }

    @GetMapping("/appointments")
    public String appointments(HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        model.addAttribute("appointments", appointmentService.findAllForDoctor(doctor));
        return "doctor/appointments";
    }

    @GetMapping("/consultations")
    public String consultations(HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        model.addAttribute("consultations", consultationService.findByDoctor(doctor));
        return "doctor/consultations";
    }

    @GetMapping("/consultations/{id}")
    public String consultationDetail(@PathVariable("id") Long id, HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        Consultation consultation = consultationService.getConsultation(id);
        if (!consultation.getDoctor().getId().equals(doctor.getId())) {
            throw new BusinessException("That consultation belongs to another doctor.");
        }
        model.addAttribute("consultation", consultation);
        return "doctor/consultation-detail";
    }

    /**
     * THE &lt;&lt;include&gt;&gt; "View Patient Medical History".
     *
     * Reached from the queue before starting a consultation, and from the
     * consultation form itself.
     */
    @GetMapping("/patients/{patientId}/history")
    public String patientHistory(@PathVariable("patientId") Long patientId, Model model) {
        model.addAttribute("history", consultationService.viewPatientHistory(patientId));
        model.addAttribute("patient", consultationService.getPatient(patientId));
        return "doctor/patient-history";
    }

    // ------------------------------------------------------------------
    // UC-02 Record consultation notes + digital prescription
    // ------------------------------------------------------------------

    @GetMapping("/consultation/new")
    public String consultationForm(@RequestParam(name = "appointmentId") Long appointmentId, HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        Appointment appointment = appointmentService.getAppointment(appointmentId);
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new BusinessException("That appointment belongs to another doctor.");
        }

        ConsultationForm form = new ConsultationForm();
        form.setAppointmentId(appointmentId);
        model.addAttribute("consultationForm", form);
        prepareConsultationModel(model, appointment);
        return "doctor/consultation-form";
    }

    @PostMapping("/consultation/new")
    public String saveConsultation(@Valid @ModelAttribute("consultationForm") ConsultationForm consultationForm,
                                   BindingResult binding,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes redirect) {
        Doctor doctor = currentDoctor(session);
        Appointment appointment = appointmentService.getAppointment(consultationForm.getAppointmentId());

        if (binding.hasErrors()) {
            prepareConsultationModel(model, appointment);
            return "doctor/consultation-form";
        }
        try {
            consultationService.recordConsultation(doctor, consultationForm);
            redirect.addFlashAttribute("success",
                    "Consultation recorded for " + appointment.getPatient().getFullName()
                            + ". Any prescription is now waiting in the pharmacy queue.");
            return "redirect:/doctor/consultations";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            prepareConsultationModel(model, appointment);
            return "doctor/consultation-form";
        }
    }

    /**
     * The consultation form needs three things besides the form object: the
     * appointment being recorded, the medicine list for the dropdowns, and the
     * patient's past history shown alongside (the include relationship).
     */
    private void prepareConsultationModel(Model model, Appointment appointment) {
        model.addAttribute("appointment", appointment);
        model.addAttribute("medicines", pharmacyService.findMedicines(null));
        model.addAttribute("history", consultationService.viewPatientHistory(appointment.getPatient().getId()));
    }

    private Doctor currentDoctor(HttpSession session) {
        SessionUser sessionUser = AuthInterceptor.currentUser(session);
        return authService.requireDoctor(sessionUser);
    }
}

package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.dto.FeedbackForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.model.User;
import com.healthconnect.service.AuthService;
import com.healthconnect.service.FeedbackService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * PATIENT FEEDBACK AND SUPPORT MANAGEMENT MODULE - Perera M.K.S.N. - UC-05.
 *
 * Three audiences, one data model:
 *   /patient/feedback  - the patient submits and reviews their own feedback
 *   /doctor/feedback   - the doctor sees the ratings they received
 *   /support/feedback  - the Patient Relations Officer answers comments
 */
@Controller
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuthService authService;

    public FeedbackController(FeedbackService feedbackService, AuthService authService) {
        this.feedbackService = feedbackService;
        this.authService = authService;
    }

    // ---------------------------- patient ----------------------------

    @GetMapping("/patient/feedback")
    public String myFeedback(HttpSession session, Model model) {
        Patient patient = currentPatient(session);
        model.addAttribute("feedbackList", feedbackService.findForPatient(patient));
        model.addAttribute("rateable", feedbackService.findRateableVisits(patient));
        return "patient/feedback";
    }

    @GetMapping("/patient/feedback/new")
    public String feedbackForm(@RequestParam(name = "appointmentId", required = false) Long appointmentId,
                               HttpSession session,
                               Model model) {
        Patient patient = currentPatient(session);
        FeedbackForm form = new FeedbackForm();
        form.setAppointmentId(appointmentId);
        form.setRating(5);
        model.addAttribute("feedbackForm", form);
        model.addAttribute("rateable", feedbackService.findRateableVisits(patient));
        return "patient/feedback-form";
    }

    @PostMapping("/patient/feedback/new")
    public String submitFeedback(@Valid @ModelAttribute("feedbackForm") FeedbackForm feedbackForm,
                                 BindingResult binding,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirect) {
        Patient patient = currentPatient(session);
        if (binding.hasErrors()) {
            model.addAttribute("rateable", feedbackService.findRateableVisits(patient));
            return "patient/feedback-form";
        }
        try {
            feedbackService.submitFeedback(patient, feedbackForm);
            redirect.addFlashAttribute("success", "Thank you - your feedback has been recorded.");
            return "redirect:/patient/feedback";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("rateable", feedbackService.findRateableVisits(patient));
            return "patient/feedback-form";
        }
    }

    // ---------------------------- doctor ----------------------------

    @GetMapping("/doctor/feedback")
    public String doctorFeedback(HttpSession session, Model model) {
        Doctor doctor = currentDoctor(session);
        model.addAttribute("feedbackList", feedbackService.findForDoctor(doctor));
        model.addAttribute("averageRating", feedbackService.averageRatingFor(doctor));
        model.addAttribute("feedbackCount", feedbackService.countFor(doctor));
        model.addAttribute("doctor", doctor);
        return "doctor/feedback";
    }

    // ------------------- patient relations officer -------------------

    @GetMapping("/support/dashboard")
    public String supportDashboard(Model model) {
        model.addAttribute("unanswered", feedbackService.findUnanswered());
        model.addAttribute("totalCount", feedbackService.findAll().size());
        return "support/dashboard";
    }

    @GetMapping("/support/feedback")
    public String supportFeedback(@RequestParam(name = "filter", defaultValue = "all") String filter, Model model) {
        model.addAttribute("feedbackList",
                "unanswered".equalsIgnoreCase(filter) ? feedbackService.findUnanswered() : feedbackService.findAll());
        model.addAttribute("filter", filter);
        return "support/feedback";
    }

    @PostMapping("/support/feedback/{id}/respond")
    public String respond(@PathVariable("id") Long id,
                          @RequestParam(name = "response") String response,
                          HttpSession session,
                          RedirectAttributes redirect) {
        try {
            SessionUser sessionUser = AuthInterceptor.currentUser(session);
            User officer = authService.getUser(sessionUser.getUserId());
            feedbackService.respond(id, officer, response);
            redirect.addFlashAttribute("success", "Response saved.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/support/feedback";
    }

    private Patient currentPatient(HttpSession session) {
        return authService.requirePatient(AuthInterceptor.currentUser(session));
    }

    private Doctor currentDoctor(HttpSession session) {
        return authService.requireDoctor(AuthInterceptor.currentUser(session));
    }
}

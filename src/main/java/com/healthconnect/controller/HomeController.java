package com.healthconnect.controller;

import com.healthconnect.config.AuthInterceptor;
import com.healthconnect.config.Routes;
import com.healthconnect.dto.LoginForm;
import com.healthconnect.dto.PasswordResetForm;
import com.healthconnect.dto.ProfileForm;
import com.healthconnect.dto.RegistrationForm;
import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ACCOUNT AND ACCESS MODULE (shared by all six members).
 *
 * Register Account, Login, Logout, Reset Password, Manage Profile.
 */
@Controller
public class HomeController {

    private final AuthService authService;

    public HomeController(AuthService authService) {
        this.authService = authService;
    }

    /** "/" sends you to your own dashboard, or to the login page. */
    @GetMapping("/")
    public String index(HttpSession session) {
        SessionUser currentUser = (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
        return currentUser == null ? "redirect:/login" : "redirect:" + Routes.homeFor(currentUser.getRole());
    }

    // ------------------------------------------------------------------
    // Login / logout
    // ------------------------------------------------------------------

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        SessionUser currentUser = (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
        if (currentUser != null) {
            return "redirect:" + Routes.homeFor(currentUser.getRole());
        }
        model.addAttribute("loginForm", new LoginForm());
        return "auth/login";
    }

    @PostMapping("/login")
    public String doLogin(@Valid @ModelAttribute("loginForm") LoginForm loginForm,
                          BindingResult binding,
                          HttpSession session,
                          Model model) {
        if (binding.hasErrors()) {
            return "auth/login";
        }
        try {
            SessionUser sessionUser = authService.login(loginForm.getEmail(), loginForm.getPassword());
            // THIS LINE is what "being logged in" means in this application.
            session.setAttribute(AuthInterceptor.SESSION_KEY, sessionUser);
            return "redirect:" + Routes.homeFor(sessionUser.getRole());
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/login";
        }
    }

    @RequestMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout";
    }

    // ------------------------------------------------------------------
    // Patient self-registration
    // ------------------------------------------------------------------

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String doRegister(@Valid @ModelAttribute("registrationForm") RegistrationForm registrationForm,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.registerPatient(registrationForm);
            redirect.addFlashAttribute("success", "Your account has been created. Please log in.");
            return "redirect:/login";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
    }

    // ------------------------------------------------------------------
    // Reset password
    // ------------------------------------------------------------------

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("passwordResetForm", new PasswordResetForm());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String doResetPassword(@Valid @ModelAttribute("passwordResetForm") PasswordResetForm passwordResetForm,
                                  BindingResult binding,
                                  Model model,
                                  RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "auth/forgot-password";
        }
        try {
            authService.resetPassword(passwordResetForm);
            redirect.addFlashAttribute("success", "Your password has been changed. Please log in.");
            return "redirect:/login";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/forgot-password";
        }
    }

    // ------------------------------------------------------------------
    // Manage profile (any logged-in role)
    // ------------------------------------------------------------------

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        SessionUser currentUser = (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
        model.addAttribute("profileForm", authService.buildProfileForm(currentUser.getUserId()));
        return "auth/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@Valid @ModelAttribute("profileForm") ProfileForm profileForm,
                              BindingResult binding,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirect) {
        SessionUser currentUser = (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
        if (binding.hasErrors()) {
            return "auth/profile";
        }
        try {
            authService.updateProfile(currentUser.getUserId(), profileForm);
            // Keep the name in the navbar in step with the change.
            currentUser.setFullName(profileForm.getFullName());
            redirect.addFlashAttribute("success", "Your profile has been updated.");
            return "redirect:/profile";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/profile";
        }
    }

    /** Shown when a logged-in user reaches a URL meant for another role. */
    @GetMapping("/denied")
    public String denied() {
        return "error/denied";
    }
}

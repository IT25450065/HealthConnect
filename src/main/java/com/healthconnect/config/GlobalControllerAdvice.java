package com.healthconnect.config;

import com.healthconnect.dto.SessionUser;
import com.healthconnect.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Two things that apply to EVERY controller in the application.
 *
 * 1. @ModelAttribute("currentUser") runs before every controller method and
 *    puts the logged-in user into the model, so the shared navbar fragment can
 *    print the name and role without every controller having to add it.
 *
 * 2. @ExceptionHandler is the safety net. Controllers catch BusinessException
 *    themselves where they want to re-show a form with a red message; anything
 *    that slips through lands here and is shown on a tidy page instead of a
 *    stack trace.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpSession session) {
        return (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/business";
    }
}

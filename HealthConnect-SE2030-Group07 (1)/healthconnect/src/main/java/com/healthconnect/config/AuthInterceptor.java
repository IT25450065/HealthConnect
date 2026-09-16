package com.healthconnect.config;

import com.healthconnect.dto.SessionUser;
import com.healthconnect.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ROLE-BASED PAGE ACCESS.
 *
 * A HandlerInterceptor is a plain Java class that Spring MVC runs BEFORE the
 * controller method. preHandle() returning false stops the request, so a
 * patient who types /admin/staff into the address bar never reaches
 * AdminController at all.
 *
 * Two checks, in order:
 *   1. Is anybody logged in? (is there a SessionUser in the HttpSession?)
 *   2. Does that person's role appear in the rule for this URL prefix?
 *
 * This is the "Patient should not be able to reach Admin or Doctor screens by
 * guessing a URL" requirement, in about twenty lines of code that anyone who
 * knows Java can read.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** The key the logged-in user is stored under in the HttpSession. */
    public static final String SESSION_KEY = "currentUser";

    /**
     * URL prefix -> roles allowed to reach it.
     *
     * A LinkedHashMap keeps insertion order, so the first matching prefix wins
     * and the rules read top to bottom like a checklist.
     */
    private static final Map<String, Set<Role>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("/patient", EnumSet.of(Role.PATIENT));
        RULES.put("/doctor", EnumSet.of(Role.DOCTOR));
        RULES.put("/pharmacy", EnumSet.of(Role.PHARMACIST));
        RULES.put("/reception", EnumSet.of(Role.RECEPTIONIST, Role.ADMIN));
        RULES.put("/support", EnumSet.of(Role.PATIENT_RELATIONS_OFFICER, Role.ADMIN));
        RULES.put("/admin", EnumSet.of(Role.ADMIN));
    }

    /**
     * Convenience for the controllers: who is logged in on this session?
     * Returns null when nobody is.
     */
    public static SessionUser currentUser(HttpSession session) {
        return (SessionUser) session.getAttribute(SESSION_KEY);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        SessionUser currentUser = (SessionUser) session.getAttribute(SESSION_KEY);

        // (1) Not logged in -> send to the login page.
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login?required");
            return false;
        }

        // (2) Logged in, but is this URL meant for their role?
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Map.Entry<String, Set<Role>> rule : RULES.entrySet()) {
            if (path.equals(rule.getKey()) || path.startsWith(rule.getKey() + "/")) {
                if (!rule.getValue().contains(currentUser.getRole())) {
                    response.sendRedirect(request.getContextPath() + "/denied");
                    return false;
                }
                break;
            }
        }

        return true; // allowed - carry on to the controller
    }
}

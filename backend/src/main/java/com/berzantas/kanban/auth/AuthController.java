package com.berzantas.kanban.auth;

import com.berzantas.kanban.auth.dto.LoginRequest;
import com.berzantas.kanban.auth.dto.MeResponse;
import com.berzantas.kanban.auth.dto.ResendRequest;
import com.berzantas.kanban.auth.dto.SignupRequest;
import com.berzantas.kanban.email.EmailVerificationService;
import com.berzantas.kanban.email.VerificationOutcome;
import com.berzantas.kanban.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Local authentication endpoints. Uses the app's own JSON contract, not the Spring form-login page. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationService emailVerificationService;
    private final String frontendBaseUrl;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          EmailVerificationService emailVerificationService,
                          @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.emailVerificationService = emailVerificationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest,
                            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        // Rotate the session id after successful authentication to defend against session fixation.
        // Guard on an existing session because changeSessionId() throws if none exists; when none
        // exists, saveContext creates a fresh session below, which carries no fixation risk.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return toMeResponse((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        VerificationOutcome outcome = emailVerificationService.verify(token);
        String target = switch (outcome) {
            case VERIFIED -> frontendBaseUrl + "/verify?verified=true";
            case EXPIRED -> frontendBaseUrl + "/verify?error=expired";
            case INVALID -> frontendBaseUrl + "/verify?error=invalid";
        };
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendRequest request) {
        authService.resend(request.email());
    }

    @GetMapping("/me")
    public MeResponse me() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return toMeResponse(principal);
    }

    private MeResponse toMeResponse(UserPrincipal principal) {
        return new MeResponse(principal.getId(), principal.getUsername(),
                principal.getDisplayName(), principal.isEnabled());
    }
}

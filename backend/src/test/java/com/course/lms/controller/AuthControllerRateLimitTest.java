package com.course.lms.controller;

import com.course.lms.dto.AuthResponse;
import com.course.lms.dto.LoginRequest;
import com.course.lms.service.AuthService;
import com.course.lms.service.GoogleAuthService;
import com.course.lms.service.LoginRateLimiter;
import com.course.lms.service.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Force brute : après 5 mots de passe faux, la 6e tentative reçoit 429 sans vérifier le mot de passe. */
class AuthControllerRateLimitTest {

    private AuthService authService;
    private SystemLogService systemLogService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        systemLogService = mock(SystemLogService.class);
        LoginRateLimiter limiter = new LoginRateLimiter(5, 20, 5, 20, 15);
        mvc = MockMvcBuilders.standaloneSetup(
                new AuthController(authService, mock(GoogleAuthService.class), limiter, systemLogService)).build();
    }

    private static MockHttpServletRequestBuilder login(String password) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"etudiant@lms.tn\",\"password\":\"" + password + "\"}")
                .with(request -> { request.setRemoteAddr("41.226.1.1"); return request; });
    }

    @Test
    void sixthAttemptIsRefusedWith429() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("bad"));

        for (int i = 0; i < 5; i++) {
            mvc.perform(login("faux" + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("encore-faux"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("8[5-9][0-9]|900"))) // ~15 min
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Réessayez dans 15 min")));

        // Le 6e essai n'atteint même pas la vérification du mot de passe ; le blocage est écrit une fois dans le journal
        verify(authService, times(5)).login(any(LoginRequest.class));
        verify(systemLogService, times(1)).record(anyString(), eq("AUTH"), eq("Connexion bloquée temporairement"),
                anyString(), eq("etudiant@lms.tn"));
    }

    @Test
    void correctPasswordStillWorksBeforeTheLimit() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("bad"), new BadCredentialsException("bad"))
                .thenReturn(new AuthResponse());

        mvc.perform(login("faux1")).andExpect(status().isUnauthorized());
        mvc.perform(login("faux2")).andExpect(status().isUnauthorized());
        mvc.perform(login("bon")).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordIsLimitedTo5MailsPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"etudiant@lms.tn\"}")).andExpect(status().isOk());
        }
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"etudiant@lms.tn\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        verify(authService, times(5)).forgotPassword("etudiant@lms.tn");
    }
}

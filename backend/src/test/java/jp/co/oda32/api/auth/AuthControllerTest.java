package jp.co.oda32.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.oda32.TestApplication;
import jp.co.oda32.config.JwtAuthenticationFilter;
import jp.co.oda32.config.JwtTokenProvider;
import jp.co.oda32.config.SecurityConfig;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.dto.auth.LoginRequest;
import jp.co.oda32.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = TestApplication.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserDetailsService userDetailsService;

    private MLoginUser createTestLoginUser() {
        MLoginUser mLoginUser = new MLoginUser();
        mLoginUser.setLoginUserNo(1);
        mLoginUser.setUserName("テストユーザー");
        mLoginUser.setLoginId("admin");
        mLoginUser.setPassword("encoded-password");
        mLoginUser.setCompanyNo(1);
        mLoginUser.setCompanyType("admin");
        return mLoginUser;
    }

    @Test
    void login_success() throws Exception {
        MLoginUser mLoginUser = createTestLoginUser();
        LoginUser loginUser = new LoginUser(mLoginUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest request = new LoginRequest();
        request.setLoginId("admin");
        request.setPassword("password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.loginId").value("admin"))
                .andExpect(jsonPath("$.user.userName").value("テストユーザー"));
    }

    @Test
    void login_badCredentials() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setLoginId("wrong");
        request.setPassword("wrong");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_validationError_blankLoginId() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLoginId("");
        request.setPassword("password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_withValidToken() throws Exception {
        MLoginUser mLoginUser = createTestLoginUser();
        LoginUser loginUser = new LoginUser(mLoginUser);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(loginUser);

        String token = jwtTokenProvider.generateToken("admin");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("admin"));
    }

    @Test
    void me_withoutToken_returns401or403() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 but got " + status);
                });
    }

    @Test
    void protectedEndpoint_withoutToken_returns401or403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/sales-summary"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 but got " + status);
                });
    }
}

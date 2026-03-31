package jp.co.oda32.api.auth;

import jp.co.oda32.config.JwtTokenProvider;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.dto.auth.LoginRequest;
import jp.co.oda32.dto.auth.LoginResponse;
import jp.co.oda32.dto.auth.UserInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLoginId(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .user(UserInfoResponse.from(loginUser.getUser()))
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(UserInfoResponse.from(loginUser.getUser()));
    }

}

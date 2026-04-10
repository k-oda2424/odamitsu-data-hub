package jp.co.oda32.api.user;

import jakarta.validation.Valid;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.login.LoginUserService;
import jp.co.oda32.dto.user.UserCreateRequest;
import jp.co.oda32.dto.user.UserResponse;
import jp.co.oda32.dto.user.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final LoginUserService loginUserService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String loginId) {
        List<MLoginUser> users = loginUserService.find(null, userName, loginId, Flag.NO);
        return ResponseEntity.ok(
                users.stream().map(UserResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/{loginUserNo}")
    public ResponseEntity<UserResponse> get(@PathVariable Integer loginUserNo) {
        MLoginUser user = loginUserService.get(loginUserNo);
        if (user == null || Flag.YES.getValue().equals(user.getDelFlg())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody UserCreateRequest request) throws Exception {
        MLoginUser existing = loginUserService.findByLoginId(request.getLoginId());
        if (existing != null && !Flag.YES.getValue().equals(existing.getDelFlg())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "このログインIDは既に使用されています"));
        }

        MLoginUser saved = loginUserService.insert(
                request.getLoginId(), request.getUserName(), request.getPassword());
        return ResponseEntity.ok(UserResponse.from(saved));
    }

    @PutMapping("/{loginUserNo}")
    public ResponseEntity<?> update(
            @PathVariable Integer loginUserNo,
            @Valid @RequestBody UserUpdateRequest request) throws Exception {
        MLoginUser user = loginUserService.get(loginUserNo);
        if (user == null || Flag.YES.getValue().equals(user.getDelFlg())) {
            return ResponseEntity.notFound().build();
        }

        MLoginUser existing = loginUserService.findByLoginId(request.getLoginId());
        if (existing != null && !existing.getLoginUserNo().equals(loginUserNo)
                && !Flag.YES.getValue().equals(existing.getDelFlg())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "このログインIDは既に使用されています"));
        }

        user.setLoginId(request.getLoginId());
        user.setUserName(request.getUserName());
        loginUserService.updateUser(user);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            loginUserService.updatePassword(user.getLoginId(), request.getPassword());
        }

        MLoginUser updated = loginUserService.get(loginUserNo);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @DeleteMapping("/{loginUserNo}")
    public ResponseEntity<?> delete(
            @PathVariable Integer loginUserNo,
            @AuthenticationPrincipal LoginUser currentUser) throws Exception {
        if (currentUser.getUser().getLoginUserNo().equals(loginUserNo)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "自分自身は削除できません"));
        }

        MLoginUser user = loginUserService.get(loginUserNo);
        if (user == null || Flag.YES.getValue().equals(user.getDelFlg())) {
            return ResponseEntity.notFound().build();
        }

        loginUserService.deleteUser(user);
        return ResponseEntity.noContent().build();
    }
}

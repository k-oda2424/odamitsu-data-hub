package jp.co.oda32.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank(message = "ログインIDは必須です")
    private String loginId;

    @NotBlank(message = "ユーザー名は必須です")
    private String userName;

    @Size(min = 5, max = 16, message = "パスワードは5〜16文字で入力してください")
    private String password;
}

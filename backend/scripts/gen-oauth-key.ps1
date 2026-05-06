# MF OAuth 用 暗号鍵生成 script (P1-05 案 C.3)
#
# 用途:
#   APP_CRYPTO_OAUTH_KEY  (パスフレーズ, 32 byte 乱数を base64)
#   APP_CRYPTO_OAUTH_SALT (PBKDF2 salt, 16 byte 乱数を hex)
#   を生成する。OauthCryptoUtil は salt を hex 必須としているため、salt は hex 出力にする。
#
# 実行例:
#   cd backend\scripts
#   .\gen-oauth-key.ps1
#
# 生成後の手順 (claudedocs/runbook-mf-oauth-keys.md Step 2):
#   1. IntelliJ Run Configuration -> Environment Variables に下記を追加
#      APP_CRYPTO_OAUTH_KEY=<上で表示された key>
#      APP_CRYPTO_OAUTH_SALT=<上で表示された salt>
#   2. backend を起動 -> Flyway V033 ログ "[V033] OAuth secrets re-encrypted: ..." を確認
#
# 注意:
#   - dev / prod で必ず別の鍵を生成すること
#   - 既存暗号化データは V033 が自動で再暗号化するので、手動 DELETE 不要
#   - 生成した key/salt は秘匿情報。git に commit しない / Slack 等に貼らない

$keyBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($keyBytes)
$key = [Convert]::ToBase64String($keyBytes)

$saltBytes = New-Object byte[] 16
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($saltBytes)
$salt = -join ($saltBytes | ForEach-Object { $_.ToString("x2") })

Write-Host ""
Write-Host "=== APP_CRYPTO_OAUTH_KEY (32 byte base64) ===" -ForegroundColor Green
Write-Host $key
Write-Host ""
Write-Host "=== APP_CRYPTO_OAUTH_SALT (16 byte hex) ===" -ForegroundColor Green
Write-Host $salt
Write-Host ""
Write-Host "次の手順: claudedocs/runbook-mf-oauth-keys.md の Step 2 (IntelliJ Run Configuration)" -ForegroundColor Yellow
Write-Host ""

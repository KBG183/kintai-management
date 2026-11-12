[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
param($WorkerId, $LedgerPath, $ManagerPath)
for ($i = 1; $i -le 10; $i++) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ManagerPath" -Action CHECK_STATUS -LedgerPath "$LedgerPath"
    Start-Sleep -Milliseconds (Get-Random -Minimum 50 -Maximum 120)
}
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
param($LedgerPath)
$mutexName = "Global\Kintai_LedgerLock_" + (([System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($LedgerPath)) | ForEach-Object { '{0:x2}' -f $_ }) -join '').Substring(0, 16)
$created = $false
$mutex = New-Object System.Threading.Mutex($true, $mutexName, [ref]$created)
Start-Sleep -Seconds 10
param([string]$LedgerPath, [string]$ReadyEventName)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$mutexName = "Global\Kintai_LedgerLock_" + (([System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($LedgerPath)) | ForEach-Object { "{0:x2}" -f $_ }) -join '').Substring(0, 16)
$created = $false
$mutex = New-Object System.Threading.Mutex($false, $mutexName, [ref]$created)
$mutex.WaitOne() | Out-Null

$ev = [System.Threading.EventWaitHandle]::OpenExisting($ReadyEventName)
$ev.Set() | Out-Null

Start-Sleep -Seconds 30
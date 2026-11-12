param(
    [string]$WorkerId,
    [string]$LedgerPath,
    [string]$BarrierName,
    [string]$LogFile
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ev = [System.Threading.EventWaitHandle]::OpenExisting($BarrierName)
$ev.WaitOne()

$mutexName = "Global\Kintai_LedgerLock_" + (([System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($LedgerPath)) | ForEach-Object { "{0:x2}" -f $_ }) -join '').Substring(0, 16)
$created = $false
$mutex = New-Object System.Threading.Mutex($false, $mutexName, [ref]$created)

$tReq = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")
$mutex.WaitOne() | Out-Null
$tAcq = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")
$tEnterCS = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")

Start-Sleep -Milliseconds 250

$tExitCS = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")
$mutex.ReleaseMutex()
$tRel = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")

$line = "{0},{1},{2},{3},{4},{5},{6}" -f $WorkerId, $PID, $tReq, $tAcq, $tEnterCS, $tExitCS, $tRel
[System.IO.File]::AppendAllText($LogFile, "$line`r`n", [System.Text.Encoding]::UTF8)
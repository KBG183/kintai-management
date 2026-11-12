param(
    [string]$WorkerId,
    [string]$LedgerPath,
    [string]$ManagerPath,
    [string]$BarrierName,
    [string]$LogFile
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ev = [System.Threading.EventWaitHandle]::OpenExisting($BarrierName)
$ev.WaitOne() # Wait for synchronized release

$reqTime = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ManagerPath" -Action CHECK_STATUS -LedgerPath "$LedgerPath"
$sw.Stop()
$acqTime = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss.fff")

$line = "{0},{1},{2},{3},{4}ms" -f $WorkerId, $PID, $reqTime, $acqTime, $sw.ElapsedMilliseconds
[System.IO.File]::AppendAllText($LogFile, "$line`r`n", [System.Text.Encoding]::UTF8)
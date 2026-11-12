param([string]$LedgerPath, [string]$EventName)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$tmpFile = "$LedgerPath.tmp"
$partialData = '{"current_state": "CORRUPTED_MID_WRITE_FAIL", "data": "incomplete_payload_1234567890"}'
[System.IO.File]::WriteAllText($tmpFile, $partialData, [System.Text.Encoding]::UTF8)

$ev = [System.Threading.EventWaitHandle]::OpenExisting($EventName)
$ev.Set() | Out-Null

Start-Sleep -Seconds 30
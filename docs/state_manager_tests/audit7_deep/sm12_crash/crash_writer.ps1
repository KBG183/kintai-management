[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
param($LedgerPath)
$tmpFile = "$LedgerPath.tmp"
$partialData = '{"current_state": "CORRUPTED_MID_WRITE", "data": "incom'
[System.IO.File]::WriteAllText($tmpFile, $partialData, [System.Text.Encoding]::UTF8)
Start-Sleep -Seconds 10
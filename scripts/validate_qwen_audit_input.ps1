[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BundlePath,

    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [switch]$EmitJson
)

$ErrorActionPreference = 'Stop'

function Fail-Validation([string]$Code, [string]$Message) {
    $script:Failures += [pscustomobject]@{ code = $Code; message = $Message }
}

$script:Failures = @()
$bundle = $null
$manifest = $null

try {
    $bundle = Get-Item -LiteralPath $BundlePath -ErrorAction Stop
    if ($bundle.PSIsContainer) {
        Fail-Validation 'BUNDLE_NOT_FILE' 'Bundle must be a regular file.'
    } elseif ($bundle.Length -le 0) {
        Fail-Validation 'BUNDLE_EMPTY' 'Bundle file is empty.'
    }
} catch {
    Fail-Validation 'BUNDLE_MISSING' $_.Exception.Message
}

try {
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($null -eq $manifest -or $manifest -isnot [pscustomobject]) { throw 'Manifest must be a JSON object.' }
} catch {
    Fail-Validation 'MANIFEST_INVALID' $_.Exception.Message
}

if ($null -ne $manifest) {
    $required = @('target_day', 'requirements', 'rtm_items', 'test_ids', 'execution_logs', 'junit_xml', 'environment')
    foreach ($name in $required) {
        $property = $manifest.PSObject.Properties[$name]
        if ($null -eq $property -or $null -eq $property.Value -or ([string]::IsNullOrWhiteSpace([string]$property.Value) -and $name -eq 'environment')) {
            Fail-Validation 'METADATA_MISSING' "Required manifest field is missing: $name"
        }
    }

    foreach ($name in @('requirements', 'rtm_items', 'test_ids')) {
        $property = $manifest.PSObject.Properties[$name]
        if ($null -ne $property -and (@($property.Value).Count -eq 0 -or @($property.Value | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -eq 0)) {
            Fail-Validation 'METADATA_EMPTY' "Required manifest field is empty: $name"
        }
    }
    if ([string]::IsNullOrWhiteSpace([string]$manifest.target_day)) {
        Fail-Validation 'METADATA_EMPTY' 'Required manifest field is empty: target_day'
    }

    $bundleHash = if ($null -ne $bundle -and -not $bundle.PSIsContainer) {
        (Get-FileHash -LiteralPath $bundle.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    } else { $null }

    if ([string]$manifest.bundle_sha256 -notmatch '^[0-9a-fA-F]{64}$') {
        Fail-Validation 'HASH_MISSING_OR_INVALID' 'A valid SHA-256 bundle hash is required.'
    } elseif ($bundleHash -and $manifest.bundle_sha256.ToLowerInvariant() -ne $bundleHash) {
        Fail-Validation 'HASH_MISMATCH' "Manifest hash does not match the bundle: $bundleHash"
    }

    foreach ($pathProperty in @('execution_logs', 'junit_xml')) {
        $values = @($manifest.$pathProperty)
        if ($values.Count -eq 0) {
            Fail-Validation 'EVIDENCE_PATH_MISSING' "No paths supplied for $pathProperty"
            continue
        }
        foreach ($path in $values) {
            if ([string]::IsNullOrWhiteSpace([string]$path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
                Fail-Validation 'EVIDENCE_NOT_FOUND' "Evidence file does not exist: $path"
                continue
            }
            $evidence = Get-Item -LiteralPath $path
            if ($evidence.Length -eq 0) {
                Fail-Validation 'EVIDENCE_EMPTY' "Evidence file is empty: $path"
                continue
            }
            if ($pathProperty -eq 'junit_xml') {
                try {
                    $settings = New-Object System.Xml.XmlReaderSettings
                    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
                    $settings.XmlResolver = $null
                    $reader = [System.Xml.XmlReader]::Create($evidence.FullName, $settings)
                    try { $xml = New-Object System.Xml.XmlDocument; $xml.XmlResolver = $null; $xml.Load($reader) } finally { $reader.Dispose() }
                    if ($xml.DocumentElement.LocalName -notin @('testsuite', 'testsuites')) { throw 'Root element must be testsuite or testsuites.' }
                    $suites = @($xml.SelectNodes('//testsuite'))
                    $cases = @($xml.SelectNodes('//testcase'))
                    if ($cases.Count -eq 0) { Fail-Validation 'JUNIT_NO_TESTS' "No test cases in: $path" }
                    if ($cases.Count -gt 0 -and @($xml.SelectNodes('//testcase[not(skipped)]')).Count -eq 0) { Fail-Validation 'JUNIT_ALL_SKIPPED' "All test cases are skipped: $path" }
                    if (@($xml.SelectNodes('//testcase/failure | //testcase/error')).Count -gt 0 -or @($suites | Where-Object { ([int]$_.GetAttribute('failures') -gt 0) -or ([int]$_.GetAttribute('errors') -gt 0) }).Count -gt 0) {
                        Fail-Validation 'JUNIT_FAILURE' "Failures or errors in: $path"
                    }
                } catch {
                    Fail-Validation 'JUNIT_INVALID' "Invalid JUnit XML: $path; $($_.Exception.Message)"
                }
            }
        }
    }
}

$result = [pscustomobject]@{
    status = if ($script:Failures.Count -eq 0) { 'INPUT_READY' } else { 'INPUT_INCOMPLETE' }
    bundle_path = if ($null -ne $bundle) { $bundle.FullName } else { $BundlePath }
    manifest_path = $ManifestPath
    bundle_sha256 = if ($null -ne $bundle -and -not $bundle.PSIsContainer) { (Get-FileHash -LiteralPath $bundle.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null }
    failures = @($script:Failures)
}

if ($EmitJson) {
    $result | ConvertTo-Json -Depth 5
} else {
    $result | Format-List
}

if ($script:Failures.Count -gt 0) { exit 2 }
exit 0

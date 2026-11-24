# Pubblica una nuova versione di TapiTranscriber nelle Releases di github.com/tapinilife/tapitranscriber (pubblico).
# Il telefono la vede da solo e propone "Aggiorna".
#
# Uso:  .\strumenti\pubblica.ps1 -Note "Cosa cambia in questa versione"
# Le credenziali GitHub sono quelle già salvate da Git (Git Credential Manager): niente chiavi nel progetto.

param(
    [Parameter(Mandatory = $true)][string]$Note,
    [switch]$SenzaCompilare
)

$ErrorActionPreference = "Stop"
$repo = "tapinilife/tapitranscriber"
$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

Push-Location $root
try {
    if (-not $SenzaCompilare) {
        Write-Host "Compilo la versione release..."
        & .\gradlew.bat assembleRelease --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "Compilazione fallita." }
    }
    $meta = Get-Content "app\build\outputs\apk\release\output-metadata.json" -Raw | ConvertFrom-Json
    $el = $meta.elements[0]
    $code = $el.versionCode
    $name = $el.versionName
    $apk = Join-Path $root "app\build\outputs\apk\release\$($el.outputFile)"
    $tag = "v$name-$code"
    $asset = "TapiTranscriber-$name-$code.apk"
    Write-Host "Versione $name ($code) -> tag $tag"

    # Token GitHub dalle credenziali già salvate di Git (non viene mai stampato).
    # Windows PowerShell altera il testo passato con "|": uso un file temporaneo con a capo Unix.
    $req = [IO.Path]::GetTempFileName()
    [IO.File]::WriteAllText($req, "protocol=https`nhost=github.com`n`n")
    $cred = cmd /c "git credential fill < `"$req`""
    Remove-Item $req
    $token = ($cred | Where-Object { $_ -like "password=*" }) -replace '^password=', ''
    if (-not $token) { throw "Credenziali GitHub non trovate: fai un 'git push' manuale una volta per salvarle." }
    $headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json"; "User-Agent" = "tapitranscriber-pubblica" }

    $body = @{ tag_name = $tag; target_commitish = "main"; name = "TapiTranscriber $name"; body = $Note } | ConvertTo-Json
    $rel = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers `
        -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType "application/json; charset=utf-8"

    $upload = "https://uploads.github.com/repos/$repo/releases/$($rel.id)/assets?name=$asset"
    $null = Invoke-RestMethod -Method Post -Uri $upload -Headers $headers -InFile $apk -ContentType "application/vnd.android.package-archive"

    Write-Host "Pubblicata: $($rel.html_url)"
    Write-Host "Download diretto: https://github.com/$repo/releases/download/$tag/$asset"
}
finally {
    Pop-Location
}

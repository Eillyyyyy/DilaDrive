param(
    [int]$Port = 8080,
    [switch]$UseConfiguredDatabasePassword,
    [switch]$UseWindowsTrust,
    [switch]$EnablePush,
    [string]$FirebaseCredentials
)
$ErrorActionPreference = 'Stop'
$previousCredentials = $env:GOOGLE_APPLICATION_CREDENTIALS
$previousProject = $env:FIREBASE_PROJECT_ID
$previousPush = $env:BOOKING_PUSH_ENABLED
$previousDatasourcePassword = $env:SPRING_DATASOURCE_PASSWORD
$credentials = if ($FirebaseCredentials) { $FirebaseCredentials } else { $previousCredentials }
if ($credentials) {
    $credentials = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($credentials)
}
Push-Location $PSScriptRoot
try {
    if ($EnablePush -or $FirebaseCredentials -or $env:BOOKING_PUSH_ENABLED -eq 'true') {
        $config = Get-Content -LiteralPath '../../mobile-app/android/app/google-services.json' -Raw | ConvertFrom-Json
        $project = $config.project_info.project_id
        if (!$credentials -or !(Test-Path -LiteralPath $credentials -PathType Leaf)) {
            throw "FCM requires a service-account JSON for '$project'. Set GOOGLE_APPLICATION_CREDENTIALS or pass -FirebaseCredentials."
        }
        $credentialPath = (Resolve-Path -LiteralPath $credentials).Path
        try {
            $account = Get-Content -LiteralPath $credentialPath -Raw | ConvertFrom-Json -ErrorAction Stop
        } catch {
            throw 'FCM credentials could not be read as service-account JSON.'
        }
        if ($account.type -ne 'service_account' -or $account.project_id -ne $project -or !$account.private_key) {
            throw "FCM credentials must be a service account for '$project', the project configured in the Android app."
        }
        $env:GOOGLE_APPLICATION_CREDENTIALS = $credentialPath
        $env:FIREBASE_PROJECT_ID = $project
        $env:BOOKING_PUSH_ENABLED = 'true'
        Write-Host "FCM enabled for $project."
    }
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    $mavenPath = if ($maven) { $maven.Source } else { Join-Path $env:USERPROFILE '.maven\maven-3.9.15\bin\mvn.cmd' }
    if (!(Test-Path -LiteralPath $mavenPath)) { throw 'Install Maven and add mvn to PATH.' }
    if (!$UseConfiguredDatabasePassword) {
        $env:SPRING_DATASOURCE_PASSWORD = 'postgres'
    }
    $launchArgs = @('spring-boot:run', '-Dspring-boot.run.profiles=local', "-Dspring-boot.run.arguments=--server.port=$Port")
    if ($UseWindowsTrust) {
        $launchArgs += '-Dspring-boot.run.jvmArguments=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
    }
    & $mavenPath @launchArgs
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally {
    $env:SPRING_DATASOURCE_PASSWORD = $previousDatasourcePassword
    $env:GOOGLE_APPLICATION_CREDENTIALS = $previousCredentials
    $env:FIREBASE_PROJECT_ID = $previousProject
    $env:BOOKING_PUSH_ENABLED = $previousPush
    Pop-Location
}

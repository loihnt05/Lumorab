param(
    [string]$Container = "lumora-postgres-1",
    [string]$DbUser = "lumora",
    [string]$DbName = "homestay_booking",
    [string]$OutputDir = ".\backups"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$backupFile = Join-Path $OutputDir "$DbName-$timestamp.dump"

Write-Host "Creating custom-format PostgreSQL dump: $backupFile"
docker exec $Container pg_dump -U $DbUser -d $DbName -Fc > $backupFile
Write-Host "Backup completed successfully: $backupFile"

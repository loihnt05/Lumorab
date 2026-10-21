param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Container = "lumora-postgres-1",
    [string]$DbUser = "lumora",
    [string]$TargetDb = "homestay_booking_restore_drill"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

Write-Host "Recreating target database '$TargetDb' for restore drill..."
docker exec $Container psql -U $DbUser -d postgres -c "DROP DATABASE IF EXISTS $TargetDb;"
docker exec $Container psql -U $DbUser -d postgres -c "CREATE DATABASE $TargetDb ENCODING 'UTF8';"

Write-Host "Restoring '$BackupFile' into '$TargetDb'..."
Get-Content -Raw -Encoding Byte $BackupFile | docker exec -i $Container pg_restore -U $DbUser -d $TargetDb --no-owner --no-privileges

Write-Host "Verifying restored Flyway schema history..."
docker exec $Container psql -U $DbUser -d $TargetDb -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
Write-Host "Restore drill completed successfully."

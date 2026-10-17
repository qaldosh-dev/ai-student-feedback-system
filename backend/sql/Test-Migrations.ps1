# Opt-in PostgreSQL integration check. Creates NEW databases only; never reuses or deletes a database.
# Requires PostgreSQL tools, Maven, Java 21 and a packaged backend. No real emails/AI/imports.
[CmdletBinding()]
param([switch]$Run, [string]$HostName = 'localhost', [int]$Port = 5432)
$ErrorActionPreference = 'Stop'
if (!$Run) { throw 'Explicit opt-in required: ./sql/Test-Migrations.ps1 -Run' }
if (!$env:DB_USERNAME -or !$env:DB_PASSWORD) { throw 'Set DB_USERNAME and DB_PASSWORD locally first. Never put secrets in command arguments.' }
$backend = (Resolve-Path "$PSScriptRoot/..").Path
$root = (Resolve-Path "$backend/..").Path
$jar = Join-Path $backend 'target/student-feedback-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Run mvn.cmd package first.' }
$prefix = 'opt09_check_' + (Get-Date -Format 'yyyyMMddHHmmss') + '_' + ([guid]::NewGuid().ToString('N').Substring(0,6))
$logDirectory = Join-Path $root "work/$prefix"
New-Item -ItemType Directory -Path $logDirectory | Out-Null
$saved = @{}
foreach ($key in @('DB_URL','PGHOST','PGPORT','PGUSER','PGPASSWORD','PGOPTIONS','PGCONNECT_TIMEOUT')) {
    $saved[$key] = [Environment]::GetEnvironmentVariable($key)
}
$created = [System.Collections.Generic.List[string]]::new()
$process = $null

function Sql([string]$database, [string]$query) {
    $result = & psql -X -w -d $database -Atq -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) { throw "SQL check failed in $database" }
    return ($result -join "`n").Trim()
}
function Verify([string]$database) {
    & psql -X -w -d $database -v ON_ERROR_STOP=1 -f "$PSScriptRoot/verify-v1-schema.sql" > "$logDirectory/$database-verifier.log" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Schema mismatch in $database; see verifier log." }
}
function Flyway([string]$database, [string]$goal, [string]$label, [switch]$ExpectFailure) {
    if (!$created.Contains($database)) { throw 'Refusing a database not created by this run.' }
    $env:DB_URL = "jdbc:postgresql://${HostName}:${Port}/$database"
    & mvn.cmd -B -ntp -Pdb-migrations "flyway:$goal" > "$logDirectory/$label.log" 2>&1
    $failed = $LASTEXITCODE -ne 0
    if ($failed -ne [bool]$ExpectFailure) { throw "Unexpected Flyway result: $label; see log." }
}
function Fingerprint([string]$database) {
    $tables = (Sql $database "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history' ORDER BY tablename") -split "`n"
    return ($tables | ForEach-Object {
        $table = $_.Trim()
        if ($table -notmatch '^[a-z_]+$') { throw 'Unexpected table name.' }
        Sql $database "SELECT '$table',count(*),md5(coalesce(string_agg(md5(row_to_json(t)::text),'' ORDER BY md5(row_to_json(t)::text)),'')) FROM public.$table t"
    }) -join "`n"
}
try {
    Push-Location $backend
    $env:PGHOST=$HostName; $env:PGPORT=[string]$Port; $env:PGUSER=$env:DB_USERNAME
    $env:PGPASSWORD=$env:DB_PASSWORD; $env:PGCONNECT_TIMEOUT='5'
    $env:PGOPTIONS='-c statement_timeout=30000 -c lock_timeout=5000'
    foreach ($suffix in @('fresh','adopt','incompatible')) {
        $database = "${prefix}_$suffix"
        & createdb -w --maintenance-db=postgres $database
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create a dedicated test database. No existing database will be used.' }
        $created.Add($database)
    }
    $fresh=$created[0]; $adopt=$created[1]; $incompatible=$created[2]

    Flyway $fresh migrate 'fresh-first-migrate'
    Verify $fresh
    $empty = Fingerprint $fresh
    if (($empty -split "`n") | Where-Object { ($_ -split '\|')[1] -ne '0' }) { throw 'V1 inserted application rows.' }
    if ((Sql $fresh 'SELECT count(*) FROM public.flyway_schema_history WHERE version=''1'' AND success') -ne '1') { throw 'V1 was not recorded.' }
    $history = Sql $fresh 'SELECT count(*) FROM public.flyway_schema_history'
    Flyway $fresh migrate 'fresh-second-migrate'
    if ((Fingerprint $fresh) -ne $empty -or (Sql $fresh 'SELECT count(*) FROM public.flyway_schema_history') -ne $history) { throw 'Second migrate changed state.' }

    # A compatible pre-Flyway schema with ONE synthetic row, not a copy of private production data.
    & psql -X -w -d $adopt -v ON_ERROR_STOP=1 -1 -f "$backend/src/main/resources/db/migration/V1__current_schema.sql" > "$logDirectory/adopt-schema.log" 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare the adoption fixture.' }
    Sql $adopt "INSERT INTO public.teachers(name,department) VALUES ('Schema adoption fixture',NULL)" | Out-Null
    Verify $adopt
    $before = Fingerprint $adopt
    $sequenceBefore = Sql $adopt 'SELECT last_value,is_called FROM public.teachers_id_seq'
    # Nonempty unbaselined schemas must refuse migrate even when structurally compatible.
    Flyway $adopt migrate 'adopt-migrate-refused' -ExpectFailure
    Flyway $adopt baseline 'explicit-test-only-baseline'
    Flyway $adopt migrate 'adopt-migrate'
    Flyway $adopt validate 'adopt-history-validate'
    Verify $adopt
    if ((Fingerprint $adopt) -ne $before -or (Sql $adopt 'SELECT last_value,is_called FROM public.teachers_id_seq') -ne $sequenceBefore) { throw 'Baseline modified application data or sequence state.' }
    if ((Sql $adopt "SELECT type FROM public.flyway_schema_history WHERE version='1'") -ne 'BASELINE') { throw 'Expected an explicit baseline, not executed V1.' }

    Sql $incompatible 'CREATE TABLE public.schema_test_marker(id integer)' | Out-Null
    & psql -X -w -d $incompatible -v ON_ERROR_STOP=1 -f "$PSScriptRoot/verify-v1-schema.sql" > "$logDirectory/incompatible-verifier.log" 2>&1
    if ($LASTEXITCODE -eq 0) { throw 'Verifier accepted an incompatible schema.' }
    Flyway $incompatible migrate 'incompatible-migrate-refused' -ExpectFailure
    if ((Sql $incompatible "SELECT count(*) FROM pg_tables WHERE schemaname='public'") -ne '1') { throw 'Incompatible database was modified.' }

    # Startup is read-only and non-web. The managed profile also disables both seeds.
    $env:DB_URL="jdbc:postgresql://${HostName}:${Port}/$fresh`?options=-c%20default_transaction_read_only%3Don"
    $process = Start-Process -FilePath (Get-Command java).Source -WindowStyle Hidden -PassThru -WorkingDirectory $backend `
        -ArgumentList @('-jar', ('"'+$jar+'"'), '--spring.profiles.active=db-managed', '--spring.main.web-application-type=none') `
        -RedirectStandardOutput "$logDirectory/mapping-startup.log" -RedirectStandardError "$logDirectory/mapping-startup-error.log"
    if (!$process.WaitForExit(60000)) { throw 'Mapping validation process did not finish within 60 seconds.' }
    $process.Refresh()
    if ($process.ExitCode -ne 0 -or !(Select-String -Path "$logDirectory/mapping-startup.log" -SimpleMatch 'Started FeedbackApplication' -Quiet)) {
        throw 'Hibernate validate startup failed; see logs.'
    }
    Verify $fresh
    if ((Fingerprint $fresh) -ne $empty) { throw 'Application startup seeded data.' }
    @('PASS: V1 on fresh PostgreSQL; verifier; no seed rows.',
      'PASS: second migrate is a no-op.',
      'PASS: explicit baseline preserves a synthetic compatible schema and its row/sequence.',
      'PASS: nonempty unbaselined and incompatible schemas are refused.',
      'PASS: packaged backend starts with Hibernate validate and read-only connection.',
      'Main database was not connected to. Test databases retained; no destructive cleanup.') | Tee-Object -FilePath "$logDirectory/result.txt"
} finally {
    if ($process -and !$process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
    Pop-Location
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
    Write-Output "Logs: $logDirectory"
    Write-Output ('Test databases retained (no DROP permitted): ' + ($created -join ', '))
}

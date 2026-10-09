param(
    [string]$CredentialFile = 'D:\Program Files\develop software\MySql8.0+\memconi-mysql-credentials.txt',
    [string]$MysqlExecutable = 'D:\Program Files\develop software\MySql8.0+\mysql-8.4.9-winx64\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot 'src\main\resources\application-local.yml'
if (Test-Path -LiteralPath $configPath) { throw 'Local database configuration already exists. It has not been overwritten.' }
$credentials = @{}
foreach ($line in (Get-Content -LiteralPath $CredentialFile -Encoding UTF8)) {
    if ($line -match '^\s*([^:]+):\s*(.*)$') { $credentials[$Matches[1].Trim()] = $Matches[2].Trim() }
}
if (-not $credentials['Root password']) { throw 'Root credentials are missing.' }
$databaseHost = $credentials['Host']
$databasePort = $credentials['Port']
$databaseRootUser = $credentials['Root user']
if ($databaseHost -notmatch '^[a-zA-Z0-9.:-]+$' -or $databasePort -notmatch '^\d+$') { throw 'Invalid database endpoint.' }
$previousMysqlPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $credentials['Root password']
    $mysqlArgs = @('--protocol=TCP', "--host=$databaseHost", "--port=$databasePort", "--user=$databaseRootUser", '--batch', '--skip-column-names')
    $existing = & $MysqlExecutable @mysqlArgs -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='hlsystem';"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to connect to MySQL.' }
    if ($existing.Trim() -ne '0') { throw 'Database hlsystem already exists. No existing data has been changed.' }
    $appPassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $sql = "CREATE DATABASE hlsystem CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER 'hlsystem'@'localhost' IDENTIFIED BY '$appPassword'; GRANT ALL PRIVILEGES ON hlsystem.* TO 'hlsystem'@'localhost';"
    & $MysqlExecutable @mysqlArgs -e $sql
    if ($LASTEXITCODE -ne 0) { throw 'Database provisioning failed.' }
    $localConfig = "spring:`n  datasource:`n    url: jdbc:mysql://${databaseHost}:${databasePort}/hlsystem?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai`n    username: hlsystem`n    password: $appPassword`napp:`n  demo:`n    enabled: true`n    password: '123456'`n"
    [System.IO.File]::WriteAllText($configPath, $localConfig, [System.Text.UTF8Encoding]::new($false))
    $env:MYSQL_PWD = $appPassword
    & $MysqlExecutable '--protocol=TCP' "--host=$databaseHost" "--port=$databasePort" '--user=hlsystem' '--database=hlsystem' -e 'SELECT VERSION() AS version, DATABASE() AS project_database;'
    if ($LASTEXITCODE -ne 0) { throw 'Project account verification failed.' }
    'Project database and local configuration are ready. Credentials were not printed.'
} finally { $env:MYSQL_PWD = $previousMysqlPassword }

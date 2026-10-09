param([string]$ConnectionFile = (Join-Path $PSScriptRoot '..\.runtime\database-connection.json'))
$ErrorActionPreference='Stop'
$connection=Get-Content -LiteralPath $ConnectionFile -Raw -Encoding UTF8 | ConvertFrom-Json
if($connection.database -ne 'hlsystem' -or $connection.username -notmatch '^[a-zA-Z0-9_]+$'){throw 'Invalid project database parameters.'}
$mysqlExecutable='D:\Program Files\develop software\MySql8.0+\mysql-8.4.9-winx64\bin\mysql.exe'
$previousPassword=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$connection.password
    $mysqlArgs=@('--protocol=TCP',"--host=$($connection.host)","--port=$($connection.port)","--user=$($connection.username)")
    $result=& $mysqlExecutable @mysqlArgs '--batch' '--skip-column-names' -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='hlsystem';"
    if($LASTEXITCODE -ne 0){throw 'Provided account could not connect to MySQL.'}
    if($result.Trim() -ne '1'){
        $credentialLines=Get-Content -LiteralPath 'D:\Program Files\develop software\MySql8.0+\memconi-mysql-credentials.txt' -Encoding UTF8
        $rootPassword=($credentialLines | Where-Object {$_ -match '^Root password:'}) -replace '^Root password:\s*',''
        if(-not $rootPassword){throw 'Project database grants need an administrator account.'}
        $env:MYSQL_PWD=$rootPassword
        & $mysqlExecutable '--protocol=TCP' "--host=$($connection.host)" "--port=$($connection.port)" '--user=root' -e "GRANT ALL PRIVILEGES ON hlsystem.* TO '$($connection.username)'@'localhost';"
        if($LASTEXITCODE -ne 0){throw 'Unable to grant project database access.'}
        $env:MYSQL_PWD=$connection.password
    }
    & $mysqlExecutable @mysqlArgs '--database=hlsystem' -e 'SELECT VERSION() AS version, DATABASE() AS project_database, CURRENT_USER() AS project_user;'
    if($LASTEXITCODE -ne 0){throw 'Project database access check failed.'}
    $quotedPassword=$connection.password.Replace("'","''")
    $configuration="spring:`n  datasource:`n    url: jdbc:mysql://$($connection.host):$($connection.port)/hlsystem?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai`n    username: $($connection.username)`n    password: '$quotedPassword'`napp:`n  demo:`n    enabled: true`n    password: '123456'`n"
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'src\main\resources\application-local.yml'),$configuration,[System.Text.UTF8Encoding]::new($false))
    'Provided account configured. Password was not printed.'
} finally {$env:MYSQL_PWD=$previousPassword}

$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runtimePath=Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$localConfig=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\application-local.yml') -Raw -Encoding UTF8
$databasePassword=[regex]::Match($localConfig,"(?m)^    password: '([^']*)'").Groups[1].Value
$databaseUsername=[regex]::Match($localConfig,'(?m)^    username:\s*(\S+)').Groups[1].Value
if(-not $databasePassword -or -not $databaseUsername){throw 'Local database configuration is missing.'}
$backupPath=Join-Path $runtimePath ('before-expanded-demo-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.sql')
$priorPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=$databasePassword
    & 'D:\Program Files\develop software\MySql8.0+\mysql-8.4.9-winx64\bin\mysqldump.exe' '--protocol=TCP' '--host=127.0.0.1' '--port=3307' "--user=$databaseUsername" '--single-transaction' '--no-tablespaces' '--set-gtid-purged=OFF' "--result-file=$backupPath" 'hlsystem'
    if($LASTEXITCODE -ne 0){throw 'Backup failed; demo import has not started.'}
}finally{$env:MYSQL_PWD=$priorPassword}
$jarPath=Join-Path $PSScriptRoot 'target\hlsystem-0.1.0.jar'
$classesPath=Join-Path $PSScriptRoot 'target\classes'
if(-not (Test-Path -LiteralPath (Join-Path $classesPath 'com\hlsystem\system\PopulateDemoData.class'))){throw 'Compile the backend before importing the expanded dataset.'}
$libraryPath=Join-Path $runtimePath 'demo-libraries'
New-Item -ItemType Directory -Path $libraryPath -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::OpenRead($jarPath)
try{
    foreach($entry in $archive.Entries){
        if($entry.FullName.StartsWith('BOOT-INF/lib/') -and $entry.Name.EndsWith('.jar')){
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libraryPath $entry.Name),$true)
        }
    }
}finally{$archive.Dispose()}
Push-Location $PSScriptRoot
try{
    & java '-Dfile.encoding=UTF-8' -cp "$classesPath;$libraryPath\*" 'com.hlsystem.system.PopulateDemoData'
    if($LASTEXITCODE -ne 0){throw "Import failed. Database backup: $backupPath"}
}finally{Pop-Location}
"Database backup: $backupPath"

param([int]$Port=8080)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runtimePath=Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$jarPath=Join-Path $PSScriptRoot 'target\hlsystem-0.1.0.jar'
if(-not (Test-Path -LiteralPath $jarPath)){throw 'Build the backend first: mvn -DskipTests package'}
$probe=[System.Net.Sockets.TcpClient]::new()
try{$probe.Connect('127.0.0.1',$Port);throw "Port $Port is already in use."}catch [System.Net.Sockets.SocketException]{}finally{$probe.Dispose()}
$stdoutPath=Join-Path $runtimePath 'backend.log'
$stderrPath=Join-Path $runtimePath 'backend-error.log'
$process=Start-Process -FilePath 'java' -ArgumentList @('-jar',"`"$jarPath`"","--server.port=$Port") -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
[System.IO.File]::WriteAllText((Join-Path $runtimePath 'backend.pid'),[string]$process.Id)
"Backend starting: http://127.0.0.1:$Port"
"PID: $($process.Id). Log: $stdoutPath"

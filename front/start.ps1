param([int]$Port=5173)
$ErrorActionPreference='Stop'
$runtimePath=Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime'
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$vitePath=Join-Path $PSScriptRoot 'node_modules\vite\bin\vite.js'
if(-not (Test-Path -LiteralPath $vitePath)){throw 'Install frontend dependencies first: npm install'}
$probe=[System.Net.Sockets.TcpClient]::new()
try{$probe.Connect('127.0.0.1',$Port);throw "Port $Port is already in use."}catch [System.Net.Sockets.SocketException]{}finally{$probe.Dispose()}
$process=Start-Process -FilePath 'node' -ArgumentList @("`"$vitePath`"",'--host','127.0.0.1','--port',"$Port",'--strictPort') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtimePath 'frontend.log') -RedirectStandardError (Join-Path $runtimePath 'frontend-error.log') -PassThru
[System.IO.File]::WriteAllText((Join-Path $runtimePath 'frontend.pid'),[string]$process.Id)
"Frontend starting: http://127.0.0.1:$Port"

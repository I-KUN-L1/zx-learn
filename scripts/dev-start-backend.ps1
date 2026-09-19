# ==========================================================
# 知行智学 ZhiXing Learn —— 后端一键启动（Windows / PowerShell）
# ----------------------------------------------------------
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\dev-start-backend.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\dev-start-backend.ps1 zx-user zx-course zx-auth zx-gateway
#
# 背景：见 scripts/dev-start-backend.sh 顶部说明。
# 宿主环境注入的 SERVER__PORT / SERVER__HOST 会被 Spring 松散绑定为
# server.port / server.host，覆盖 application.yml 导致端口冲突而启动失败；
# 同时 %TMP% 可能指向不可写的 C:\Windows\。本脚本启动前统一清理/修正。
# ==========================================================

param([string[]]$Modules)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

# ---------- 定位 Maven ----------
$Mvn = $env:MVN
if (-not $Mvn) {
  $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if ($cmd) {
    $Mvn = $cmd.Source
  } elseif (Test-Path "D:\1\apache-maven-3.9.6\bin\mvn.cmd") {
    $Mvn = "D:\1\apache-maven-3.9.6\bin\mvn.cmd"
  } else {
    throw "未找到 Maven，请设置环境变量 MVN"
  }
}

$TmpLocal = Join-Path $Root "logs\tmp"
New-Item -ItemType Directory -Force -Path $TmpLocal | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Root "logs") | Out-Null

# ---------- 关键：清除宿主注入的端口变量 ----------
Remove-Item Env:SERVER__PORT -ErrorAction SilentlyContinue
Remove-Item Env:SERVER__HOST -ErrorAction SilentlyContinue

# 沙箱下 %TMP% 可能指向不可写的 C:\Windows\，统一指向仓库内可写目录
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$TmpLocal"

if (-not $Modules -or $Modules.Count -eq 0) {
  $Modules = @(
    "zx-user","zx-course","zx-auth","zx-gateway","zx-exam","zx-media",
    "zx-learning","zx-trade","zx-promotion","zx-aigc","zx-pay","zx-search",
    "zx-remark","zx-message","zx-data","zx-insight"
  )
}

Write-Host "仓库根目录: $Root"
Write-Host "Maven     : $Mvn"
Write-Host "启动服务  : $($Modules -join ', ')"
Write-Host "----------------------------------------------------------"

foreach ($m in $Modules) {
  Start-Process -FilePath $Mvn `
    -ArgumentList "-pl", $m, "spring-boot:run" `
    -WorkingDirectory $Root `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $Root "logs\$m.dev.log") `
    -RedirectStandardError  (Join-Path $Root "logs\$m.dev.err.log")
  Write-Host "  -> $m 已启动   日志: logs\$m.dev.log"
  Start-Sleep -Milliseconds 800
}

Write-Host "----------------------------------------------------------"
Write-Host "已启动 $($Modules.Count) 个服务。网关入口 http://localhost:8080"

# =============================================================
# 知行智学 性能压测启动脚本（JMeter 四场景 · 并发阶梯）
#
# 用法（在 PowerShell 中运行）：
#   .\run-perf.ps1 -JmeterBin D:\apache-jmeter-5.6.3\bin\jmeter.bat
#   .\run-perf.ps1 -Scenario all
#   .\run-perf.ps1 -Scenario 2 -Threads "50,100" -Loops 200
#   .\run-perf.ps1 -Scenario 1 -Threads "50" -Loops 500
#
# 默认会跑 4 个场景 × 4 级并发(50/100/200/500)。
# 三个场景均为循环型（极简结构 JMX），每线程循环 -Loops 次，通过循环量控制采样规模。
# 结果输出到：$ResultsDir（时间戳目录），含每档 .csv + HTML 报告。
#
# 注意 scenario3(SSE 对话) 是「并发长连接容量」压测：
#   SSE 响应单向推送、读完 END 连接即收尾，无法复用同一连接多发请求；
#   Windows 客户端端口约 16K 个(TIME_WAIT 240s)，若用 高并发×高 Loops（如 200×500）
#   每轮都新建连接会在客户端侧触发 "Address already in use: getsockopt"（端口耗尽）。
#   scenario3 请改用小并发×小循环：如 -Scenario 3 -Threads "50,100" -Loops 5。
#
# 注意 scenario4(秒杀领取) 压测前必须先预热活动：
#   POST http://localhost:8088/coupons/seckill/warmup/{couponId}
#   未预热时 Lua 返回 NOT_READY，所有请求会被秒拒（无参考价值）。
#   同一 user-info 重复领取返回 REPEAT 属有效业务结果；统计超发请另比对 DB 券码数。
# =============================================================
param(
    [string]$JmeterBin = "D:\1\jmeter\bin\jmeter.bat",
    [string]$JmxDir    = "$PSScriptRoot\jmeter",
    [string]$ResultsDir = "$PSScriptRoot\results",
    [string]$Scenario  = "all",                 # all | 1 | 2 | 3 | 4
    [string]$Threads   = "50,100,200,500",      # 并发阶梯
    [int]$Loops        = 1000,                  # 每线程循环次数（控制采样规模）
    [string]$TargetHost = "localhost",          # 被测主机（可指向网关/负载均衡）；不能叫 Host，因其为 PS 只读自动变量
    [string]$CouponId   = "1",                  # 秒杀场景（Scenario 4）必传预热券 ID，否则全部 NOT_READY 秒拒
    [int]$HostConn     = 10                     # 各场景间等待冷却时间(s)
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $JmeterBin)) { Write-Error "未找到 jmeter.bat：$JmeterBin（请加 -JmeterBin 指定）"; exit 1 }
if (-not (Test-Path $JmxDir))  { Write-Error "目录不存在：$JmxDir"; exit 1 }

# 场景名 -> 计划文件 + 服务端口
$scenarios = @(
    @{ Id = 1; Name = "course-detail";  File = "$JmxDir\scenario1-course-detail.jmx";    Port = 8083; Mode = "loops" },
    @{ Id = 2; Name = "flashsale-order";File = "$JmxDir\scenario2-flashsale-order.jmx"; Port = 8087; Mode = "loops" },
    @{ Id = 3; Name = "aigc-chat";      File = "$JmxDir\scenario3-aigc-chat.jmx";        Port = 8089; Mode = "loops" },
    @{ Id = 4; Name = "seckill-claim";  File = "$JmxDir\scenario4-seckill-claim.jmx";    Port = 8088; Mode = "loops" }
)
if ($Scenario -eq "all") { $targets = $scenarios }
else { $targets = $scenarios | Where-Object { [string]$_.Id -eq $Scenario } }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ResultsDir $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "==> 结果目录：$runDir" -ForegroundColor Cyan

# 并发前置探活：确保目标服务可达，避免压测打到空端口
function Test-Port($port) {
    try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect($TargetHost, $port); $c.Close(); return $true }
    catch { return $false }
}

$levels = $Threads -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ }

foreach ($s in $targets) {
    if (-not (Test-Path $s.File)) { Write-Warning "跳过（文件不存在）：$($s.File)"; continue }
    if (-not (Test-Port $s.Port)) { Write-Warning "跳过（$TargetHost`:$($s.Port) 探活失败）：场景 $($s.Id)（$($s.Name)）"; continue }

    $logFile = Join-Path $runDir "scenario$($s.Id)-$($s.Name).log"
    Write-Host "`n===== 场景 $($s.Id)：$($s.Name)  @ $($TargetHost):$($s.Port) =====" -ForegroundColor Green

    foreach ($t in $levels) {
        $base     = "s$($s.Id)-$TargetHost-$($t)u"
        $csv      = Join-Path $runDir "$base.csv"
        $htmlDir  = Join-Path $runDir "$base-html"

        # 极简结构 JMX：全部按每线程循环次数（-JLOOPS）控制采样量
        # 注意：不能把 -JKEY=value 放在数组变量里传给 & jmeter.bat ——
        # PowerShell 5.1 会把数组 join 成单个空格串，导致 -JPORT=8083 被拆断（端口变空→80）。
        # 因此改用 cmd /c 拼接，确保每个 token 独立传参。
        $base = "s$($s.Id)-$TargetHost-$($t)u-loops${Loops}"
        $desc = "并发=$t 循环=$Loops"
        Write-Host "  [$desc] $base" -ForegroundColor Yellow

        $argsLine = "-JTHREADS=$t -JRAMP=10 -JHOST=$TargetHost -JPORT=$($s.Port) -JPROTOCOL=http -JLOOPS=$Loops -JCOUPON_ID=$CouponId"
        $cmd = "`"$JmeterBin`" -n -t `"$($s.File)`" $argsLine -l `"$csv`" -e -o `"$htmlDir`""
        & cmd.exe /c $cmd 2>&1 | Tee-Object -FilePath $logFile -Append

        if ($LASTEXITCODE -ne 0) { Write-Warning "  !! 场景$($s.Id) 并发$t 结束码=$LASTEXITCODE（详见 $logFile）" }
        else { Write-Host "  OK 完成：$csv" -ForegroundColor Green }

        if ($t -ne $levels[-1]) { Start-Sleep -Seconds $HostConn }   # 档间冷却，让服务/连接池回落
    }

    # 场景间冷却
    if ($s -ne $targets[-1]) { Start-Sleep -Seconds $HostConn }
}

Write-Host "`n===== 全部完成 =====  CSV 位于：$runDir" -ForegroundColor Cyan
Get-ChildItem $runDir -Recurse -File -Filter *.csv | ForEach-Object { Write-Output $_.FullName }
# Aggregate JMeter CSV exports: N / QPS / avg / P99 / max / error-rate per file
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File aggregate-csv.ps1 [-Dir <results-dir>] [-NameFilter <pattern>]
param(
  [string]$Dir,
  [string]$NameFilter = "*.csv"
)
$ErrorActionPreference = 'Stop'
if (-not $Dir) {
  $Dir = Get-ChildItem .\results -Directory | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
"dir: $Dir"
foreach ($f in (Get-ChildItem $Dir -Filter $NameFilter | Sort-Object Name)) {
  # PS5.1 Get-Content 默认按 GBK 解码，UTF-8 中文 label 会错位吞逗号导致列偏移，必须显式 UTF8
  $lines = Get-Content $f.FullName -Encoding UTF8
  $header = ($lines[0] -split ',')
  $iTs  = [array]::IndexOf($header, 'timeStamp')
  $iEl  = [array]::IndexOf($header, 'elapsed')
  $iRc  = [array]::IndexOf($header, 'responseCode')
  $iLabel = [array]::IndexOf($header, 'label')
  $rows = foreach ($l in ($lines | Select-Object -Skip 1)) {
    $p = $l -split ','
    # 排除预热线程组样本（WARMUP label），只聚合压测主采样
    if ($p[$iLabel] -like 'WARMUP*') { continue }
    [pscustomobject]@{ ts = [long]$p[$iTs]; el = [double]$p[$iEl]; rc = [string]$p[$iRc] }
  }
  if (-not $rows -or @($rows).Count -eq 0) { continue }
  $rows = @($rows)
  $n = $rows.Count
  $el = $rows.el | Sort-Object
  $p99 = $el[[int][math]::Ceiling($n * 0.99) - 1]
  $avg = [math]::Round(($el | Measure-Object -Average).Average, 0)
  $err = @($rows | Where-Object { $_.rc -ne '200' }).Count
  $ts  = @($rows | ForEach-Object { $_.ts }) | Sort-Object
  $thr = if ($ts[-1] -gt $ts[0]) { [math]::Round($n / (($ts[-1] - $ts[0]) / 1000.0)) } else { 0 }
  "{0,-30} N={1,6}  QPS={2,5}  avg={3,4}ms  P99={4,5}ms  max={5,6}ms  err={6,5} ({7:P2})" -f `
    $f.Name, $n, $thr, $avg, $p99, $el[-1], $err, ($err / $n)
  if ($err -gt 0 -and $f.Name -like 's3*') {
    "    [diag] rc distribution: " + ((@($rows | Group-Object rc | Sort-Object Count -Descending | Select-Object -First 3) | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' ')
  }
}

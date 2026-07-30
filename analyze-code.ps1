# Comprehensive code analysis script to reduce token usage and improve code quality

Write-Host "🔍 Running comprehensive code analysis..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "📊 Step 1: Running ktlint (code formatting)..." -ForegroundColor Yellow
& .\gradlew.bat ktlintCheck
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  ktlint found issues" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🔎 Step 2: Running detekt (static code analysis)..." -ForegroundColor Yellow
& .\gradlew.bat detekt
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  detekt found issues" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🤖 Step 3: Running Android Lint..." -ForegroundColor Yellow
& .\gradlew.bat lint
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Android Lint found issues" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "📈 Step 4: Generating code complexity report..." -ForegroundColor Yellow
& .\gradlew.bat detekt --buildUponDefaultConfig --config config/detekt/detekt.yml
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Complexity analysis incomplete" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "✨ Analysis complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "📋 Summary:" -ForegroundColor White
Write-Host "- Check reports in: app/build/reports/" -ForegroundColor Gray
Write-Host "- Detekt report: app/build/reports/detekt/" -ForegroundColor Gray
Write-Host "- Lint report: app/build/reports/lint-results.html" -ForegroundColor Gray
Write-Host "- ktlint report: app/build/reports/ktlint/" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 Tips to reduce token usage:" -ForegroundColor Cyan
Write-Host "1. Fix ktlint formatting issues: .\gradlew.bat ktlintFormat" -ForegroundColor Gray
Write-Host "2. Address detekt warnings systematically" -ForegroundColor Gray
Write-Host "3. Keep functions under 15 lines where possible" -ForegroundColor Gray
Write-Host "4. Extract complex logic into separate functions" -ForegroundColor Gray
Write-Host "5. Use data classes for data holders" -ForegroundColor Gray
Write-Host "6. Prefer composition over inheritance" -ForegroundColor Gray
Write-Host "7. Remove unused imports and dead code" -ForegroundColor Gray
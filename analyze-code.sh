#!/bin/bash
# Comprehensive code analysis script to reduce token usage and improve code quality

echo "🔍 Running comprehensive code analysis..."
echo "=========================================="

echo ""
echo "📊 Step 1: Running ktlint (code formatting)..."
./gradlew ktlintCheck || echo "⚠️  ktlint found issues"

echo ""
echo "🔎 Step 2: Running detekt (static code analysis)..."
./gradlew detekt || echo "⚠️  detekt found issues"

echo ""
echo "🤖 Step 3: Running Android Lint..."
./gradlew lint || echo "⚠️  Android Lint found issues"

echo ""
echo "📈 Step 4: Generating code complexity report..."
./gradlew detekt --buildUponDefaultConfig --config config/detekt/detekt.yml || echo "⚠️  Complexity analysis incomplete"

echo ""
echo "✨ Analysis complete!"
echo "=========================================="
echo "📋 Summary:"
echo "- Check reports in: app/build/reports/"
echo "- Detekt report: app/build/reports/detekt/"
echo "- Lint report: app/build/reports/lint-results.html"
echo "- ktlint report: app/build/reports/ktlint/"
echo ""
echo "💡 Tips to reduce token usage:"
echo "1. Fix ktlint formatting issues: ./gradlew ktlintFormat"
echo "2. Address detekt warnings systematically"
echo "3. Keep functions under 15 lines where possible"
echo "4. Extract complex logic into separate functions"
echo "5. Use data classes for data holders"
echo "6. Prefer composition over inheritance"
echo "7. Remove unused imports and dead code"
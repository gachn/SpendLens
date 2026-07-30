const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

console.log('🚀 Running all token optimization analyses...');
console.log('==============================================');

const analyses = [
    {
        name: 'Gradle ktlint',
        command: 'gradlew.bat ktlintCheck',
        description: 'Code formatting check'
    },
    {
        name: 'Gradle detekt',
        command: 'gradlew.bat detekt',
        description: 'Static code analysis'
    },
    {
        name: 'Gradle lint',
        command: 'gradlew.bat lint',
        description: 'Android lint analysis'
    },
    {
        name: 'Duplicate code',
        command: 'jscpd . --config .jscpd.json',
        description: 'Code duplication detection'
    },
    {
        name: 'Circular dependencies',
        command: 'madge --circular --extensions kt,kts,java app/src/main/java',
        description: 'Circular dependency detection'
    },
    {
        name: 'Dependency check',
        command: 'depcheck',
        description: 'Unused dependency detection'
    }
];

let passed = 0;
let failed = 0;
const results = [];

analyses.forEach((analysis, index) => {
    console.log(`\n${index + 1}. ${analysis.name} - ${analysis.description}`);
    try {
        execSync(analysis.command, { stdio: 'pipe', timeout: 60000 });
        console.log(`   ✅ PASSED`);
        results.push({ name: analysis.name, status: 'PASS' });
        passed++;
    } catch (error) {
        console.log(`   ⚠️  ISSUES FOUND (expected for some tools)`);
        results.push({ name: analysis.name, status: 'ISSUES' });
        failed++;
    }
});

console.log('\n==============================================');
console.log('📊 Summary:');
console.log(`   ✅ Passed: ${passed}`);
console.log(`   ⚠️  Issues: ${failed}`);
console.log(`   📈 Total: ${analyses.length}`);

// Generate summary report
const report = {
    timestamp: new Date().toISOString(),
    total: analyses.length,
    passed,
    failed,
    results
};

fs.writeFileSync('token-optimization-report.json', JSON.stringify(report, null, 2));
console.log('\n📋 Detailed report saved to: token-optimization-report.json');

console.log('\n💡 Next steps:');
console.log('1. Review reports in respective directories');
console.log('2. Fix critical issues first');
console.log('3. Run "gradlew.bat ktlintFormat" to auto-fix formatting');
console.log('4. Check jscpd-report.html for duplicate code');
console.log('5. Review dep-graph.png for dependencies');
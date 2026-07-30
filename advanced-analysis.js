const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

console.log('🔍 Running comprehensive code analysis...');
console.log('==========================================');

try {
    // 1. Duplicate code detection
    console.log('\n📊 Step 1: Detecting duplicate code...');
    try {
        execSync('jscpd . --config .jscpd.json', { stdio: 'inherit' });
        console.log('✅ Duplicate code analysis complete');
    } catch (error) {
        console.log('⚠️  Duplicate code found - check jscpd-report.html');
    }

    // 2. Dependency graph analysis
    console.log('\n🕸️  Step 2: Generating dependency graph...');
    try {
        execSync('madge --image dep-graph.png --extensions kt,kts,java app/src/main/java', { stdio: 'inherit' });
        console.log('✅ Dependency graph generated: dep-graph.png');
    } catch (error) {
        console.log('⚠️  Dependency graph generation had issues');
    }

    // 3. Circular dependency detection
    console.log('\n🔄 Step 3: Checking circular dependencies...');
    try {
        execSync('madge --circular --extensions kt,kts,java app/src/main/java', { stdio: 'inherit' });
        console.log('✅ Circular dependency check complete');
    } catch (error) {
        console.log('⚠️  Circular dependencies found');
    }

    // 4. Dependency analysis
    console.log('\n📦 Step 4: Analyzing dependencies...');
    try {
        execSync('depcheck', { stdio: 'inherit' });
        console.log('✅ Dependency analysis complete');
    } catch (error) {
        console.log('⚠️  Dependency issues found');
    }

    console.log('\n==========================================');
    console.log('✨ Advanced analysis complete!');
    console.log('📋 Reports generated:');
    console.log('- Duplicate code: jscpd-report.html');
    console.log('- Dependency graph: dep-graph.png');
    console.log('- Check console for circular dependencies');
    
} catch (error) {
    console.error('❌ Analysis failed:', error.message);
    process.exit(1);
}
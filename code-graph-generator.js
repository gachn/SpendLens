const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

class CodeGraphGenerator {
    constructor(projectRoot) {
        this.projectRoot = projectRoot;
        this.codebase = {
            files: [],
            dependencies: {},
            functions: {},
            classes: {},
            imports: {}
        };
    }

    generateKotlinGraph() {
        console.log('🔍 Analyzing Kotlin codebase...');
        
        const kotlinFiles = this.findFiles('app/src/main', ['.kt', '.kts']);
        
        kotlinFiles.forEach(file => {
            const content = fs.readFileSync(file, 'utf-8');
            const relativePath = path.relative(this.projectRoot, file);
            
            this.analyzeKotlinFile(relativePath, content);
        });

        return this.codebase;
    }

    findFiles(dir, extensions) {
        const files = [];
        
        if (!fs.existsSync(dir)) return files;
        
        const items = fs.readdirSync(dir, { withFileTypes: true });
        
        items.forEach(item => {
            const fullPath = path.join(dir, item.name);
            
            if (item.isDirectory() && !item.name.startsWith('.') && item.name !== 'build') {
                files.push(...this.findFiles(fullPath, extensions));
            } else if (item.isFile()) {
                const ext = path.extname(item.name);
                if (extensions.includes(ext)) {
                    files.push(fullPath);
                }
            }
        });
        
        return files;
    }

    analyzeKotlinFile(filePath, content) {
        const fileInfo = {
            path: filePath,
            lines: content.split('\n').length,
            functions: [],
            classes: [],
            imports: [],
            complexity: 0
        };

        // Extract package
        const packageMatch = content.match(/^package\s+([\w.]+)/m);
        fileInfo.package = packageMatch ? packageMatch[1] : '';

        // Extract imports
        const importMatches = content.matchAll(/^import\s+([\w.*]+)/gm);
        for (const match of importMatches) {
            fileInfo.imports.push(match[1]);
        }

        // Extract functions
        const functionMatches = content.matchAll(/fun\s+(\w+)\s*\([^)]*\)\s*[:{]/g);
        for (const match of functionMatches) {
            fileInfo.functions.push(match[1]);
        }

        // Extract classes
        const classMatches = content.matchAll/(class|interface|object)\s+(\w+)/g);
        for (const match of classMatches) {
            fileInfo.classes.push(match[2]);
        }

        // Calculate complexity (simple metric)
        fileInfo.complexity = this.calculateComplexity(content);

        this.codebase.files.push(fileInfo);
        
        // Build dependency graph
        fileInfo.imports.forEach(imp => {
            if (!this.codebase.dependencies[filePath]) {
                this.codebase.dependencies[filePath] = [];
            }
            this.codebase.dependencies[filePath].push(imp);
        });

        // Index functions
        fileInfo.functions.forEach(func => {
            if (!this.codebase.functions[func]) {
                this.codebase.functions[func] = [];
            }
            this.codebase.functions[func].push(filePath);
        });

        // Index classes
        fileInfo.classes.forEach(cls => {
            if (!this.codebase.classes[cls]) {
                this.codebase.classes[cls] = [];
            }
            this.codebase.classes[cls].push(filePath);
        });
    }

    calculateComplexity(content) {
        let complexity = 0;
        
        // Count control structures
        const controlStructures = [
            /\bif\b/g, /\belse\b/g, /\bwhen\b/g, 
            /\bfor\b/g, /\bwhile\b/g, /\btry\b/g,
            /\bcatch\b/g, /\?:/g
        ];
        
        controlStructures.forEach(regex => {
            const matches = content.match(regex);
            if (matches) complexity += matches.length;
        });

        // Count nesting levels (simplified)
        const maxNesting = this.getMaxNestingLevel(content);
        complexity += maxNesting * 2;

        return complexity;
    }

    getMaxNestingLevel(content) {
        let maxLevel = 0;
        let currentLevel = 0;
        
        const lines = content.split('\n');
        lines.forEach(line => {
            const openBraces = (line.match(/{/g) || []).length;
            const closeBraces = (line.match(/}/g) || []).length;
            
            currentLevel += openBraces - closeBraces;
            maxLevel = Math.max(maxLevel, currentLevel);
        });
        
        return maxLevel;
    }

    findRelevantCode(searchTerm, limit = 5) {
        const results = [];
        const lowerSearchTerm = searchTerm.toLowerCase();

        // Search in function names
        for (const [funcName, files] of Object.entries(this.codebase.functions)) {
            if (funcName.toLowerCase().includes(lowerSearchTerm)) {
                files.forEach(file => {
                    results.push({
                        type: 'function',
                        name: funcName,
                        file: file,
                        relevance: this.calculateRelevance(funcName, searchTerm)
                    });
                });
            }
        }

        // Search in class names
        for (const [className, files] of Object.entries(this.codebase.classes)) {
            if (className.toLowerCase().includes(lowerSearchTerm)) {
                files.forEach(file => {
                    results.push({
                        type: 'class',
                        name: className,
                        file: file,
                        relevance: this.calculateRelevance(className, searchTerm)
                    });
                });
            }
        }

        // Sort by relevance and return top results
        results.sort((a, b) => b.relevance - a.relevance);
        return results.slice(0, limit);
    }

    calculateRelevance(name, searchTerm) {
        const lowerName = name.toLowerCase();
        const lowerSearchTerm = searchTerm.toLowerCase();
        
        if (lowerName === lowerSearchTerm) return 1.0;
        if (lowerName.startsWith(lowerSearchTerm)) return 0.8;
        if (lowerName.includes(lowerSearchTerm)) return 0.6;
        
        // Calculate similarity based on common characters
        const commonChars = lowerSearchTerm.split('').filter(char => 
            lowerName.includes(char)
        ).length;
        
        return commonChars / lowerSearchTerm.length * 0.4;
    }

    generateGraphViz() {
        let graphViz = 'digraph Codebase {\n';
        graphViz += '  rankdir=LR;\n';
        graphViz += '  node [shape=box, style=rounded];\n\n';

        // Add files as nodes
        this.codebase.files.forEach(file => {
            const nodeName = this.sanitizeNodeName(file.path);
            graphViz += `  "${nodeName}" [label="${file.path}\\n${file.lines} lines\\nComplexity: ${file.complexity}"];\n`;
        });

        // Add dependencies
        for (const [file, imports] of Object.entries(this.codebase.dependencies)) {
            const fromNode = this.sanitizeNodeName(file);
            imports.forEach(imp => {
                // Find files that match this import
                const matchingFiles = this.codebase.files.filter(f => 
                    f.package === imp || f.classes.some(c => imp.endsWith(c))
                );
                
                matchingFiles.forEach(matchingFile => {
                    const toNode = this.sanitizeNodeName(matchingFile.path);
                    graphViz += `  "${fromNode}" -> "${toNode}";\n`;
                });
            });
        }

        graphViz += '}\n';
        return graphViz;
    }

    sanitizeNodeName(name) {
        return name.replace(/[^a-zA-Z0-9]/g, '_');
    }

    generateTokenOptimizationReport() {
        const report = {
            summary: {
                totalFiles: this.codebase.files.length,
                totalFunctions: Object.keys(this.codebase.functions).length,
                totalClasses: Object.keys(this.codebase.classes).length,
                totalLines: this.codebase.files.reduce((sum, f) => sum + f.lines, 0),
                avgComplexity: this.codebase.files.reduce((sum, f) => sum + f.complexity, 0) / this.codebase.files.length
            },
            highComplexityFiles: this.codebase.files
                .filter(f => f.complexity > 20)
                .sort((a, b) => b.complexity - a.complexity)
                .slice(0, 10),
            duplicateFunctions: this.findDuplicateFunctions(),
            largeFiles: this.codebase.files
                .filter(f => f.lines > 200)
                .sort((a, b) => b.lines - a.lines)
                .slice(0, 10),
            recommendations: this.generateRecommendations()
        };

        return report;
    }

    findDuplicateFunctions() {
        const duplicates = [];
        const functionSignatures = {};

        for (const [funcName, files] of Object.entries(this.codebase.functions)) {
            if (files.length > 1) {
                functionSignatures[funcName] = files;
            }
        }

        return functionSignatures;
    }

    generateRecommendations() {
        const recommendations = [];
        
        const highComplexityFiles = this.codebase.files.filter(f => f.complexity > 20);
        if (highComplexityFiles.length > 0) {
            recommendations.push({
                priority: 'HIGH',
                category: 'Complexity',
                message: `${highComplexityFiles.length} files have high complexity (>20). Consider refactoring into smaller functions.`
            });
        }

        const largeFiles = this.codebase.files.filter(f => f.lines > 200);
        if (largeFiles.length > 0) {
            recommendations.push({
                priority: 'MEDIUM',
                category: 'File Size',
                message: `${largeFiles.length} files are large (>200 lines). Consider splitting into smaller files.`
            });
        }

        const duplicateFunctions = this.findDuplicateFunctions();
        if (Object.keys(duplicateFunctions).length > 0) {
            recommendations.push({
                priority: 'MEDIUM',
                category: 'Duplication',
                message: `${Object.keys(duplicateFunctions).length} functions appear in multiple files. Consider consolidating.`
            });
        }

        return recommendations;
    }
}

// CLI usage
if (require.main === module) {
    const projectRoot = process.cwd();
    const generator = new CodeGraphGenerator(projectRoot);
    
    console.log('🔍 Generating code graph...');
    const graph = generator.generateKotlinGraph();
    
    console.log('📊 Generating optimization report...');
    const report = generator.generateTokenOptimizationReport();
    
    console.log('💾 Saving reports...');
    fs.writeFileSync('code-graph.json', JSON.stringify(graph, null, 2));
    fs.writeFileSync('token-optimization-report.json', JSON.stringify(report, null, 2));
    fs.writeFileSync('code-graph.dot', generator.generateGraphViz());
    
    console.log('✅ Code graph generation complete!');
    console.log('📋 Reports saved:');
    console.log('- code-graph.json: Full codebase analysis');
    console.log('- token-optimization-report.json: Optimization recommendations');
    console.log('- code-graph.dot: Graph visualization');
    
    // Try to generate PNG if Graphviz is available
    try {
        execSync('dot -Tpng code-graph.dot -o code-graph.png', { stdio: 'pipe' });
        console.log('- code-graph.png: Visual dependency graph');
    } catch (error) {
        console.log('💡 Install Graphviz to generate visual graph: https://graphviz.org/download/');
    }
    
    console.log('\n🔍 Example usage for code search:');
    console.log('const generator = new CodeGraphGenerator(".");');
    console.log('const results = generator.findRelevantCode("SmsProcessor");');
    
    if (process.argv.length > 2) {
        const searchTerm = process.argv[2];
        console.log(`\n🎯 Searching for: ${searchTerm}`);
        const results = generator.findRelevantCode(searchTerm);
        console.log('📍 Found:', JSON.stringify(results, null, 2));
    }
}

module.exports = CodeGraphGenerator;
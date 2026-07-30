const CodeGraphGenerator = require('./code-graph-generator');

class AutoCodeAnalyzer {
    constructor(projectRoot) {
        this.projectRoot = projectRoot;
        this.graphGenerator = new CodeGraphGenerator(projectRoot);
        this.cachedGraph = null;
        this.lastCacheTime = null;
        this.cacheTimeout = 5 * 60 * 1000; // 5 minutes
    }

    async initialize() {
        console.log('🚀 Initializing Auto Code Analyzer...');
        await this.buildGraph();
        console.log('✅ Analyzer ready');
    }

    async buildGraph() {
        const now = Date.now();
        
        // Check if we need to rebuild the graph
        if (this.cachedGraph && this.lastCacheTime && 
            (now - this.lastCacheTime) < this.cacheTimeout) {
            console.log('📊 Using cached code graph');
            return this.cachedGraph;
        }

        console.log('🔍 Building code graph...');
        this.cachedGraph = this.graphGenerator.generateKotlinGraph();
        this.lastCacheTime = now;
        console.log('✅ Code graph built');
        
        return this.cachedGraph;
    }

    async findRelevantFiles(query, context = {}) {
        const graph = await this.buildGraph();
        const results = [];
        const lowerQuery = query.toLowerCase();

        // Enhanced search with context awareness
        const searchTerms = this.extractSearchTerms(query);
        
        // Search in functions
        for (const [funcName, files] of Object.entries(graph.functions)) {
            const relevance = this.calculateEnhancedRelevance(funcName, searchTerms, context);
            if (relevance > 0.3) {
                files.forEach(file => {
                    this.addResult(results, {
                        type: 'function',
                        name: funcName,
                        file: file,
                        relevance: relevance,
                        reason: this.getMatchReason(funcName, searchTerms)
                    });
                });
            }
        }

        // Search in classes
        for (const [className, files] of Object.entries(graph.classes)) {
            const relevance = this.calculateEnhancedRelevance(className, searchTerms, context);
            if (relevance > 0.3) {
                files.forEach(file => {
                    this.addResult(results, {
                        type: 'class',
                        name: className,
                        file: file,
                        relevance: relevance,
                        reason: this.getMatchReason(className, searchTerms)
                    });
                });
            }
        }

        // Search in file paths
        graph.files.forEach(file => {
            const pathRelevance = this.calculatePathRelevance(file.path, searchTerms);
            if (pathRelevance > 0.4) {
                this.addResult(results, {
                    type: 'file',
                    name: file.path,
                    file: file.path,
                    relevance: pathRelevance,
                    reason: `Path matches query: ${file.path}`,
                    metadata: {
                        lines: file.lines,
                        complexity: file.complexity,
                        package: file.package
                    }
                });
            }
        });

        // Sort by combined relevance score
        results.sort((a, b) => b.relevance - a.relevance);
        
        return results.slice(0, 10); // Return top 10 results
    }

    extractSearchTerms(query) {
        // Split query into meaningful terms
        const terms = query.toLowerCase().split(/[\s,._-]+/);
        
        // Filter out common words and enhance terms
        const stopWords = ['the', 'a', 'an', 'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could', 'should', 'may', 'might', 'must', 'shall', 'can', 'need', 'dare', 'ought', 'used', 'to', 'of', 'in', 'for', 'on', 'with', 'at', 'by', 'from', 'as', 'into', 'through', 'during', 'before', 'after', 'above', 'below', 'between', 'under', 'again', 'further', 'then', 'once'];
        
        return terms.filter(term => term.length > 2 && !stopWords.includes(term));
    }

    calculateEnhancedRelevance(name, searchTerms, context) {
        let relevance = 0;
        const lowerName = name.toLowerCase();

        searchTerms.forEach(term => {
            // Exact match
            if (lowerName === term) {
                relevance += 1.0;
            }
            // Starts with term
            else if (lowerName.startsWith(term)) {
                relevance += 0.7;
            }
            // Contains term
            else if (lowerName.includes(term)) {
                relevance += 0.5;
            }
            // Partial match (fuzzy)
            else if (this.fuzzyMatch(lowerName, term)) {
                relevance += 0.3;
            }
        });

        // Boost based on context
        if (context.preferredTypes && context.preferredTypes.includes(this.guessType(name))) {
            relevance *= 1.3;
        }

        if (context.preferredPackages) {
            const graph = this.cachedGraph;
            const file = graph.files.find(f => 
                f.functions.includes(name) || f.classes.includes(name)
            );
            if (file && context.preferredPackages.some(pkg => file.package.startsWith(pkg))) {
                relevance *= 1.2;
            }
        }

        return Math.min(relevance, 1.0);
    }

    calculatePathRelevance(filePath, searchTerms) {
        let relevance = 0;
        const lowerPath = filePath.toLowerCase();

        searchTerms.forEach(term => {
            if (lowerPath.includes(term)) {
                // Boost for directory matches over file matches
                if (lowerPath.includes(`/${term}/`) || lowerPath.includes(`\\${term}\\`)) {
                    relevance += 0.8;
                } else {
                    relevance += 0.5;
                }
            }
        });

        return Math.min(relevance, 1.0);
    }

    fuzzyMatch(str, term) {
        // Simple fuzzy matching - check if most characters are present in order
        let strIndex = 0;
        let termIndex = 0;
        let matched = 0;
        
        while (strIndex < str.length && termIndex < term.length) {
            if (str[strIndex] === term[termIndex]) {
                matched++;
                termIndex++;
            }
            strIndex++;
        }
        
        return matched / term.length > 0.7;
    }

    guessType(name) {
        // Simple heuristic to guess if it's a UI component, business logic, etc.
        if (name.endsWith('Activity') || name.endsWith('Fragment') || name.endsWith('Screen') || name.endsWith('UI')) {
            return 'ui';
        } else if (name.endsWith('Repository') || name.endsWith('DataSource') || name.endsWith('Service')) {
            return 'data';
        } else if (name.endsWith('ViewModel') || name.endsWith('UseCase') || name.endsWith('Manager')) {
            return 'business';
        } else if (name.endsWith('Entity') || name.endsWith('Model') || name.endsWith('DTO')) {
            return 'model';
        }
        return 'unknown';
    }

    addResult(results, newResult) {
        // Avoid duplicates
        const exists = results.some(r => 
            r.type === newResult.type && 
            r.name === newResult.name && 
            r.file === newResult.file
        );
        
        if (!exists) {
            results.push(newResult);
        }
    }

    getMatchReason(name, searchTerms) {
        const matches = searchTerms.filter(term => 
            name.toLowerCase().includes(term)
        );
        return `Matches terms: ${matches.join(', ')}`;
    }

    async getCodeContext(filePath) {
        const graph = await this.buildGraph();
        const file = graph.files.find(f => f.path === filePath);
        
        if (!file) {
            return null;
        }

        return {
            file: filePath,
            package: file.package,
            functions: file.functions,
            classes: file.classes,
            imports: file.imports,
            complexity: file.complexity,
            lines: file.lines,
            dependencies: graph.dependencies[filePath] || [],
            relatedFiles: this.findRelatedFiles(filePath, graph)
        };
    }

    findRelatedFiles(filePath, graph) {
        const related = new Set();
        const file = graph.files.find(f => f.path === filePath);
        
        if (!file) {
            return [];
        }

        // Find files that import from this file
        file.classes.forEach(className => {
            const dependentFiles = graph.classes[className] || [];
            dependentFiles.forEach(f => {
                if (f !== filePath) {
                    related.add(f);
                }
            });
        });

        // Find files that this file imports
        const imports = graph.dependencies[filePath] || [];
        imports.forEach(imp => {
            graph.files.forEach(f => {
                if (f.package === imp || f.classes.some(c => imp.endsWith(c))) {
                    if (f.path !== filePath) {
                        related.add(f.path);
                    }
                }
            });
        });

        return Array.from(related);
    }

    async analyzeTokenImpact(filePath) {
        const context = await this.getCodeContext(filePath);
        if (!context) {
            return null;
        }

        // Estimate token usage for this file
        const complexityScore = context.complexity / 50; // Normalize
        const functionCount = context.functions.length;
        const classCount = context.classes.length;
        const lineCount = context.lines;

        // Simple token estimation (rough approximation)
        const estimatedTokens = lineCount * 1.5 + functionCount * 10 + classCount * 20;

        return {
            file: filePath,
            estimatedTokens: Math.round(estimatedTokens),
            complexity: context.complexity,
            functions: functionCount,
            classes: classCount,
            lines: lineCount,
            optimizationPotential: this.calculateOptimizationPotential(context),
            suggestions: this.generateOptimizationSuggestions(context)
        };
    }

    calculateOptimizationPotential(context) {
        let potential = 0;

        // High complexity indicates refactoring opportunity
        if (context.complexity > 20) {
            potential += context.complexity * 2;
        }

        // Many functions might indicate need for extraction
        if (context.functions.length > 10) {
            potential += (context.functions.length - 10) * 5;
        }

        // Large files can be split
        if (context.lines > 200) {
            potential += (context.lines - 200) / 2;
        }

        return Math.min(potential, 100); // Cap at 100%
    }

    generateOptimizationSuggestions(context) {
        const suggestions = [];

        if (context.complexity > 20) {
            suggestions.push({
                priority: 'HIGH',
                type: 'complexity',
                message: `High complexity (${context.complexity}). Consider breaking down complex functions.`,
                impact: 'high'
            });
        }

        if (context.lines > 200) {
            suggestions.push({
                priority: 'MEDIUM',
                type: 'size',
                message: `Large file (${context.lines} lines). Consider splitting into multiple files.`,
                impact: 'medium'
            });
        }

        if (context.functions.length > 15) {
            suggestions.push({
                priority: 'LOW',
                type: 'organization',
                message: `Many functions (${context.functions.length}). Consider grouping related functionality.`,
                impact: 'low'
            });
        }

        return suggestions;
    }

    async generateAnalysisReport() {
        const graph = await this.buildGraph();
        
        const report = {
            timestamp: new Date().toISOString(),
            summary: {
                totalFiles: graph.files.length,
                totalFunctions: Object.keys(graph.functions).length,
                totalClasses: Object.keys(graph.classes).length,
                totalLines: graph.files.reduce((sum, f) => sum + f.lines, 0),
                estimatedTotalTokens: this.estimateTotalTokens(graph)
            },
            highImpactFiles: await this.getHighImpactFiles(graph),
            optimizationOpportunities: this.getOptimizationOpportunities(graph),
            recommendations: this.getRecommendations(graph)
        };

        return report;
    }

    estimateTotalTokens(graph) {
        return graph.files.reduce((sum, file) => {
            return sum + Math.round(file.lines * 1.5 + file.functions.length * 10 + file.classes.length * 20);
        }, 0);
    }

    async getHighImpactFiles(graph) {
        const impacts = [];
        
        for (const file of graph.files) {
            const impact = await this.analyzeTokenImpact(file.path);
            if (impact && impact.estimatedTokens > 100) {
                impacts.push(impact);
            }
        }

        return impacts.sort((a, b) => b.estimatedTokens - a.estimatedTokens).slice(0, 20);
    }

    getOptimizationOpportunities(graph) {
        return {
            highComplexity: graph.files
                .filter(f => f.complexity > 20)
                .sort((a, b) => b.complexity - a.complexity)
                .slice(0, 10)
                .map(f => ({ file: f.path, complexity: f.complexity })),
            
            largeFiles: graph.files
                .filter(f => f.lines > 200)
                .sort((a, b) => b.lines - a.lines)
                .slice(0, 10)
                .map(f => ({ file: f.path, lines: f.lines })),
            
            duplicateCandidates: this.findDuplicateCandidates(graph)
        };
    }

    findDuplicateCandidates(graph) {
        const duplicates = [];
        
        for (const [funcName, files] of Object.entries(graph.functions)) {
            if (files.length > 1) {
                duplicates.push({
                    function: funcName,
                    files: files,
                    potential: files.length * 20 // Rough token savings estimate
                });
            }
        }

        return duplicates.sort((a, b) => b.potential - a.potential).slice(0, 10);
    }

    getRecommendations(graph) {
        return [
            {
                category: 'Immediate Actions',
                items: [
                    'Run ktlintFormat to fix formatting issues',
                    'Address high-complexity files (complexity > 20)',
                    'Review and consolidate duplicate functions',
                    'Split large files (> 200 lines) into smaller modules'
                ]
            },
            {
                category: 'Medium-term Improvements',
                items: [
                    'Implement dependency injection for better testability',
                    'Consider using sealed classes for better type safety',
                    'Extract business logic into use cases',
                    'Implement repository pattern for data access'
                ]
            },
            {
                category: 'Long-term Architecture',
                items: [
                    'Consider modularization for large features',
                    'Implement clean architecture layers',
                    'Add comprehensive unit tests',
                    'Set up CI/CD with automated analysis'
                ]
            }
        ];
    }
}

// CLI interface
if (require.main === module) {
    const analyzer = new AutoCodeAnalyzer(process.cwd());

    async function runCLI() {
        const command = process.argv[2];
        const args = process.argv.slice(3);

        try {
            switch (command) {
                case 'init':
                    await analyzer.initialize();
                    break;
                    
                case 'search':
                    await analyzer.initialize();
                    const query = args.join(' ');
                    if (!query) {
                        console.error('❌ Please provide a search query');
                        process.exit(1);
                    }
                    const results = await analyzer.findRelevantFiles(query);
                    console.log('🎯 Search results:');
                    console.log(JSON.stringify(results, null, 2));
                    break;
                    
                case 'context':
                    await analyzer.initialize();
                    const filePath = args[0];
                    if (!filePath) {
                        console.error('❌ Please provide a file path');
                        process.exit(1);
                    }
                    const context = await analyzer.getCodeContext(filePath);
                    console.log('📋 File context:');
                    console.log(JSON.stringify(context, null, 2));
                    break;
                    
                case 'impact':
                    await analyzer.initialize();
                    const impactPath = args[0];
                    if (!impactPath) {
                        console.error('❌ Please provide a file path');
                        process.exit(1);
                    }
                    const impact = await analyzer.analyzeTokenImpact(impactPath);
                    console.log('💰 Token impact analysis:');
                    console.log(JSON.stringify(impact, null, 2));
                    break;
                    
                case 'report':
                    await analyzer.initialize();
                    const report = await analyzer.generateAnalysisReport();
                    console.log('📊 Analysis report:');
                    console.log(JSON.stringify(report, null, 2));
                    
                    // Save report to file
                    const fs = require('fs');
                    fs.writeFileSync('token-optimization-report.json', JSON.stringify(report, null, 2));
                    console.log('💾 Report saved to: token-optimization-report.json');
                    break;
                    
                default:
                    console.log(`
🚀 Auto Code Analyzer - Usage:

  node auto-code-analyzer.js init                    - Initialize and build code graph
  node auto-code-analyzer.js search <query>          - Search for relevant code
  node auto-code-analyzer.js context <file>          - Get context for a file
  node auto-code-analyzer.js impact <file>           - Analyze token impact of a file
  node auto-code-analyzer.js report                  - Generate full optimization report

Examples:
  node auto-code-analyzer.js search "SmsProcessor"
  node auto-code-analyzer.js context "app/src/main/java/com/spendlens/app/SmsProcessor.kt"
  node auto-code-analyzer.js impact "app/src/main/java/com/spendlens/app/ui/MainActivity.kt"
  node auto-code-analyzer.js report
                    `);
            }
        } catch (error) {
            console.error('❌ Error:', error.message);
            process.exit(1);
        }
    }

    runCLI();
}

module.exports = AutoCodeAnalyzer;
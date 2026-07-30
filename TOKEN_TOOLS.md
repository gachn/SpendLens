# Token Usage Reduction Tools

This project includes several tools to help reduce code complexity, improve code quality, and minimize token usage when working with AI coding assistants.

## Installed Tools

### 1. **Detekt** - Static Code Analysis
- **Purpose**: Detects code smells, complexity issues, and potential bugs
- **Configuration**: `config/detekt/detekt.yml`
- **Usage**: 
  ```bash
  ./gradlew detekt
  ```
- **Auto-fix**: Some issues can be auto-fixed with `--auto-correct` flag

### 2. **ktlint** - Code Formatting
- **Purpose**: Ensures consistent code formatting across the project
- **Configuration**: Kotlin style guide + custom rules
- **Usage**:
  ```bash
  ./gradlew ktlintCheck  # Check formatting
  ./gradlew ktlintFormat # Auto-fix formatting issues
  ```

### 3. **Android Lint** - Android-Specific Analysis
- **Purpose**: Detects Android-specific issues and performance problems
- **Usage**:
  ```bash
  ./gradlew lint
  ```

### 4. **EditorConfig** - Consistent Editor Settings
- **Purpose**: Ensures consistent coding style across different editors
- **Configuration**: `.editorconfig`

## Quick Start

Run all analysis tools at once:

```bash
# On Windows
.\analyze-code.ps1

# On Unix-like systems
./analyze-code.sh
```

## Token Usage Reduction Strategies

### Code Organization
1. **Keep functions small** - Functions under 15 lines are easier to understand and use fewer tokens
2. **Extract complex logic** - Break down complex functions into smaller, focused ones
3. **Use data classes** - Data classes automatically reduce boilerplate code
4. **Prefer composition** - Use composition over inheritance to reduce code duplication

### Code Quality
1. **Remove dead code** - Delete unused functions, variables, and imports
2. **Simplify conditionals** - Use guard clauses and early returns
3. **Avoid deep nesting** - Keep nesting levels to a minimum (configured at 4)
4. **Use meaningful names** - Clear names reduce the need for comments

### Maintainability
1. **Follow Kotlin conventions** - Use idiomatic Kotlin to write concise code
2. **Leverage standard library** - Use built-in functions instead of custom implementations
3. **Minimize comments** - Write self-documenting code instead of explaining it
4. **Keep classes focused** - Single responsibility principle reduces overall complexity

## CI/CD Integration

These tools can be integrated into your CI/CD pipeline:

```yaml
# Example GitHub Actions step
- name: Run code analysis
  run: |
    ./gradlew ktlintCheck detekt lint
```

## Report Locations

- **Detekt**: `app/build/reports/detekt/`
- **ktlint**: `app/build/reports/ktlint/`
- **Android Lint**: `app/build/reports/lint-results.html`

## Configuration Files

- `config/detekt/detekt.yml` - Detekt rules and thresholds
- `.editorconfig` - Editor settings
- `build.gradle.kts` - Tool configurations

## Performance Impact

- **ktlint**: Fast, runs in seconds
- **Detekt**: Moderate, depends on codebase size
- **Android Lint**: Slower, comprehensive analysis

## Tips for Maximum Token Savings

1. **Run tools regularly** - Catch issues early when they're easier to fix
2. **Fix warnings systematically** - Address the most impactful issues first
3. **Use auto-fix features** - Let tools handle simple formatting issues
4. **Review complexity reports** - Focus on high-complexity areas first
5. **Keep dependencies minimal** - Each dependency adds potential complexity

## Troubleshooting

### Build fails after adding tools
- Check if all plugins are properly synced
- Run `./gradlew clean --no-daemon` and try again

### Tools report too many issues
- Start with critical issues first
- Adjust thresholds in `config/detekt/detekt.yml`
- Use `--auto-correct` for fixable issues

### Performance issues
- Run tools incrementally on specific modules
- Exclude test code from some analyses
- Use parallel execution where supported

## Additional Resources

- [Detekt Documentation](https://detekt.dev/)
- [ktlint Documentation](https://pinterest.github.io/ktlint/)
- [Android Lint Documentation](https://developer.android.com/studio/write/lint)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
## 0.8.0

### Security
- Package names are validated before `am force-stop`, so a rule can no longer inject shell commands
- The Plugin API switch in Settings is now honored by the receiver
- HTTP GET action accepts only http(s), logs host only, always closes the connection

### Reliability
- Shell and `su` calls have real timeouts (a hung process no longer blocks the rule thread)
- `su` availability is cached for 30 s

### Build
- CI: Gradle cache, timeout, concurrency, APK signature check; PRs build stable only
- Unit tests for package-name and URL validation

## 0.7.0

- Accessibility for APP_OPENED; UsageStats fallback; stronger sticky monitor notes

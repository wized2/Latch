# Security Policy

## Supported versions

Security fixes target the latest release on `main`.

## Reporting

Email **acartoon766@gmail.com** or use GitHub private vulnerability reporting.
Do not open public issues for exploitable vulnerabilities.

## Threat model

- Latch runs entirely on the device. There are no accounts and no analytics.
- Shell actions (Shizuku or root) run only what the user typed into a rule. Package names used in force-stop are validated against a strict pattern.
- Other apps can send `FIRE_TRIGGER` / `RUN_RULE` broadcasts. The **Plugin API** switch in Settings makes Latch ignore them.
- The `INTERNET` permission exists only for the optional HTTP GET action (http/https, host-only logs).
- Release APKs are signed in CI; the signature is verified before publishing.

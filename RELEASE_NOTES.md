# Latch 0.8.0

## Security
- Shell-injection fix for the force-stop action (package names are validated)
- Plugin API switch is now enforced
- HTTP GET action: http(s) only, host-only logging

## Reliability
- Shell / su timeouts that actually fire
- Cached su probe

## Docs
- README privacy section now matches the manifest (INTERNET is used only by HTTP GET)

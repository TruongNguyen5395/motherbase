#!/bin/bash
# Google Workspace CLI wrapper
# Ensures correct PATH and auth config on every invocation
export PATH="/config/.nvm/versions/node/v24.14.0/bin:$PATH"
export GOOGLE_WORKSPACE_CLI_KEYRING_BACKEND=file
export GOOGLE_WORKSPACE_CLI_CONFIG_DIR=/config/.config/gws
exec gws "$@"

#!/bin/bash
# Elo API - wrapper to run CLI detached from parent terminal
unset CLAUDECODE
unset CLAUDE_CODE_ENTRYPOINT
exec claude "$@"

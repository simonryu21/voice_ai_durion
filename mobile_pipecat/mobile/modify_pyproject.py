#!/usr/bin/env python3
"""Modify mobile's pyproject.toml to use local pipecat instead of PyPI version."""

import os
import sys
from pathlib import Path

# Read pyproject.toml
pyproject_path = Path("pyproject.toml")
content = pyproject_path.read_text()

# Replace pipecat-ai PyPI dependency with local path dependency
# uv supports PEP 508 format for local paths
# Use file:// protocol with absolute path (PEP 508 format)
old_dep_patterns = [
    '"pipecat-ai[webrtc,daily,silero,deepgram,openai,cartesia,google,local-smart-turn-v3,runner,mem0]"',
    '"pipecat-ai[webrtc,daily,silero,deepgram,openai,cartesia,google,local-smart-turn-v3,runner]"',  # fallback if mem0 is missing
]

# Get absolute path to parent directory (works in Docker: /app)
parent_dir = os.path.abspath("..")
# Use file:// protocol with absolute path (PEP 508 format)
# Note: file:// requires 3 slashes for absolute paths: file:///absolute/path
new_dep = f'"pipecat-ai[webrtc,daily,silero,deepgram,openai,cartesia,google,local-smart-turn-v3,runner,mem0] @ file://{parent_dir}"'

found = False
for old_dep in old_dep_patterns:
    if old_dep in content:
        content = content.replace(old_dep, new_dep)
        found = True
        break

if found:
    pyproject_path.write_text(content)
    print(f"Modified {pyproject_path} to use local pipecat")
    print(f"New dependency: {new_dep}")
    sys.exit(0)
else:
    print(f"Warning: Could not find pipecat-ai dependency in {pyproject_path}")
    print(f"Searched for: {old_dep_patterns}")
    sys.exit(1)

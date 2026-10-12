"""Pytest configuration for mobile/tests.

Adds the mobile/ directory to sys.path so tests can import siblings
(`app_catalog`, `app_inventory`, etc.) the same way `voice_ai.py` does at runtime.
"""

import sys
from pathlib import Path

_MOBILE_ROOT = Path(__file__).resolve().parent.parent
if str(_MOBILE_ROOT) not in sys.path:
    sys.path.insert(0, str(_MOBILE_ROOT))

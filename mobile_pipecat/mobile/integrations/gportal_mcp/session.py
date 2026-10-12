"""Session-to-thread mapping store.

Maps Pipecat session IDs to per-agent GPortal thread IDs so that
sub-agent conversation context is preserved across turns.
"""

from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass
class SessionThreadMapping:
    """Mapping between a Pipecat session and its GPortal sub-agent threads.

    Parameters:
        pipecat_session_id: The Pipecat session identifier.
        gportal_thread_ids: Per-agent GPortal thread IDs
            (key: agent name, value: GPortal thread ID).
    """

    pipecat_session_id: str
    gportal_thread_ids: Dict[str, str] = field(default_factory=dict)


class SessionThreadStore:
    """In-memory session-to-thread mapping store.

    For production use, swap this with a Redis or database-backed
    implementation.
    """

    def __init__(self):
        """Initialize the store."""
        self._store: Dict[str, SessionThreadMapping] = {}

    def create(self, pipecat_session_id: str) -> SessionThreadMapping:
        """Create a new mapping for a session.

        Args:
            pipecat_session_id: Pipecat session identifier.

        Returns:
            The newly created mapping.
        """
        mapping = SessionThreadMapping(pipecat_session_id=pipecat_session_id)
        self._store[pipecat_session_id] = mapping
        return mapping

    def get(self, pipecat_session_id: str) -> Optional[SessionThreadMapping]:
        """Retrieve a mapping by session ID.

        Args:
            pipecat_session_id: Pipecat session identifier.

        Returns:
            The mapping, or ``None`` if not found.
        """
        return self._store.get(pipecat_session_id)

    def update_gportal_thread(self, pipecat_session_id: str, agent_name: str, thread_id: str):
        """Update a GPortal thread ID within an existing mapping.

        Args:
            pipecat_session_id: Pipecat session identifier.
            agent_name: Agent name key (e.g. ``"mail_agent"``).
            thread_id: GPortal thread ID.
        """
        mapping = self._store.get(pipecat_session_id)
        if mapping:
            mapping.gportal_thread_ids[agent_name] = thread_id

    def remove(self, pipecat_session_id: str):
        """Remove a mapping.

        Args:
            pipecat_session_id: Pipecat session identifier.
        """
        self._store.pop(pipecat_session_id, None)

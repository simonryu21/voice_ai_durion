"""Speaker change detector based on audio features.

Detects speaker changes using pitch (F0) and energy features from audio signals.
"""

from collections import deque
from typing import Deque, Dict, Optional

import numpy as np
from loguru import logger


class SpeakerDetector:
    """
    Soniox speaker_id 사용으로 SpeakerDector는 현재 사용하지 않음
    Detects speaker changes based on audio features (pitch and energy)."""

    # Audio processing constants
    MAX_SEGMENT_FEATURES: int = 10
    INT16_MAX_VALUE: float = 32768.0

    # Pitch detection constants
    MAX_PITCH_FREQ_HZ: float = 400.0
    MIN_PITCH_FREQ_HZ: float = 80.0
    MIN_AUDIO_LENGTH_MULTIPLIER: int = 2
    WINDOW_SIZE_MULTIPLIER: int = 3

    # Speaker change detection constants
    MIN_BOTH_CHANGES_REQUIRED: int = 2
    MIN_VALUES_FOR_ALTERNATING_PATTERN: int = 4
    ALTERNATING_PATTERN_DIFF_THRESHOLD: float = 0.1
    MIN_ALTERNATING_POINTS: int = 2

    def __init__(
        self,
        *,
        sample_rate: int = 16000,
        energy_threshold: float = 0.5,
        pitch_threshold: float = 0.3,
        min_segments: int = 3,
    ):
        """Initialize speaker detector.

        Args:
            sample_rate: Audio sample rate in Hz (default: 16000)
            energy_threshold: Threshold for energy difference to detect speaker change (default: 0.3)
            pitch_threshold: Threshold for pitch difference to detect speaker change (default: 0.2)
            min_segments: Minimum number of segments with consistent changes to confirm (default: 3)
        """
        self._sample_rate = sample_rate
        self._energy_threshold = energy_threshold
        self._pitch_threshold = pitch_threshold
        self._min_segments = min_segments

        # Store audio features for each speech segment
        self._segment_features: Deque[Dict[str, float]] = deque(maxlen=self.MAX_SEGMENT_FEATURES)

    def add_audio_segment(self, audio_bytes: bytearray) -> Optional[Dict[str, float]]:
        """Process audio segment and extract features.

        Args:
            audio_bytes: Raw audio bytes in PCM format (int16)

        Returns:
            Dictionary with 'energy' and 'pitch' features, or None if processing fails
        """
        try:
            # Convert bytes to int16 numpy array
            audio_array = np.frombuffer(audio_bytes, dtype=np.int16)
            if len(audio_array) == 0:
                return None

            # Normalize to float32 range [-1.0, 1.0]
            audio_float = audio_array.astype(np.float32) / self.INT16_MAX_VALUE

            # Calculate RMS energy
            energy = self._calculate_energy(audio_float)

            # Calculate pitch (F0)
            pitch = self._calculate_pitch(audio_float)

            features = {
                "energy": energy,
                "pitch": pitch,
            }

            self._segment_features.append(features)
            return features

        except Exception as e:
            logger.debug(f"Error processing audio segment: {e}")
            return None

    def _calculate_energy(self, audio_float: np.ndarray) -> float:
        """Calculate RMS (Root Mean Square) energy.

        Args:
            audio_float: Normalized audio array in range [-1.0, 1.0]

        Returns:
            Normalized RMS energy value (0.0 to 1.0)
        """
        if len(audio_float) == 0:
            return 0.0

        # Calculate RMS energy
        rms = np.sqrt(np.mean(audio_float**2))
        return float(rms)

    def _calculate_pitch(self, audio_float: np.ndarray) -> float:
        """Calculate fundamental frequency (F0 / pitch) using autocorrelation.

        Args:
            audio_float: Normalized audio array in range [-1.0, 1.0]

        Returns:
            Pitch value in Hz, or 0.0 if not detected
        """
        if len(audio_float) == 0:
            return 0.0

        # Apply high-pass filter to remove DC component
        audio_float = audio_float - np.mean(audio_float)

        # Minimum and maximum pitch periods (in samples)
        # Human pitch range: ~80 Hz to ~400 Hz
        min_period = int(self._sample_rate / self.MAX_PITCH_FREQ_HZ)
        max_period = int(self._sample_rate / self.MIN_PITCH_FREQ_HZ)

        if len(audio_float) < max_period * self.MIN_AUDIO_LENGTH_MULTIPLIER:
            return 0.0

        # Use autocorrelation to find pitch period
        # Only analyze a window to reduce computation
        window_size = min(len(audio_float), max_period * self.WINDOW_SIZE_MULTIPLIER)
        audio_window = audio_float[:window_size]

        # Calculate autocorrelation
        autocorr = np.correlate(audio_window, audio_window, mode="full")
        autocorr = autocorr[len(autocorr) // 2 :]

        # Find peak in the pitch range
        search_range = autocorr[min_period:max_period]
        if len(search_range) == 0:
            return 0.0

        # Find the peak (excluding the first sample which is always max)
        peak_idx = np.argmax(search_range) + min_period

        # Convert period to frequency
        if peak_idx > 0:
            pitch = self._sample_rate / peak_idx
            # Clamp to reasonable range
            pitch = max(self.MIN_PITCH_FREQ_HZ, min(self.MAX_PITCH_FREQ_HZ, pitch))
            return float(pitch)

        return 0.0

    def check_speaker_change(self) -> bool:
        """Check if speaker change is detected based on recent segments.

        Returns:
            True if speaker change is detected, False otherwise
        """
        if len(self._segment_features) < self._min_segments:
            return False

        # Get recent segments
        recent_segments = list(self._segment_features)[-self._min_segments :]

        # Calculate differences between consecutive segments
        energy_diffs = []
        pitch_diffs = []

        for i in range(1, len(recent_segments)):
            prev = recent_segments[i - 1]
            curr = recent_segments[i]

            # Calculate relative energy difference
            if prev["energy"] > 0:
                energy_diff = abs(curr["energy"] - prev["energy"]) / prev["energy"]
            else:
                energy_diff = abs(curr["energy"] - prev["energy"])

            # Calculate relative pitch difference
            if prev["pitch"] > 0:
                pitch_diff = abs(curr["pitch"] - prev["pitch"]) / prev["pitch"]
            else:
                pitch_diff = abs(curr["pitch"] - prev["pitch"]) if curr["pitch"] > 0 else 0.0

            energy_diffs.append(energy_diff)
            pitch_diffs.append(pitch_diff)

        # Check if changes exceed thresholds
        # Require BOTH energy AND pitch to change significantly for speaker change
        # This reduces false positives from single speaker variations
        both_changes_count = 0
        for i in range(len(energy_diffs)):
            energy_change = energy_diffs[i] > self._energy_threshold
            pitch_change = pitch_diffs[i] > self._pitch_threshold

            # Only count when BOTH features change significantly
            if energy_change and pitch_change:
                both_changes_count += 1

        # Require at least MIN_BOTH_CHANGES_REQUIRED segments with both energy and pitch changes
        # This ensures we have clear evidence of different speakers
        if both_changes_count >= self.MIN_BOTH_CHANGES_REQUIRED:
            # Additional check: look for alternating pattern
            # This is more indicative of different speakers
            energies = [seg["energy"] for seg in recent_segments]
            pitches = [seg["pitch"] for seg in recent_segments]

            has_alternating_energy = self._check_alternating_pattern(energies)
            has_alternating_pitch = self._check_alternating_pattern(pitches)

            # Require BOTH alternating patterns to be more confident
            # Single speaker variations may show one pattern, but not both consistently
            if has_alternating_energy and has_alternating_pitch:
                logger.debug(
                    f"Speaker change detected: "
                    f"segments={len(recent_segments)}, "
                    f"both_changes={both_changes_count}/{len(energy_diffs)}, "
                    f"alternating_energy={has_alternating_energy}, "
                    f"alternating_pitch={has_alternating_pitch}"
                )
                return True
            elif both_changes_count >= (self._min_segments - 1):
                # If almost all segments show both changes, consider it a speaker change
                logger.debug(
                    f"Speaker change detected: "
                    f"segments={len(recent_segments)}, "
                    f"both_changes={both_changes_count}/{len(energy_diffs)}"
                )
                return True

        return False

    def _check_alternating_pattern(self, values: list[float]) -> bool:
        """Check if values show a clear alternating pattern (high-low-high or low-high-low).

        Args:
            values: List of feature values

        Returns:
            True if clear alternating pattern is detected
        """
        if len(values) < self.MIN_VALUES_FOR_ALTERNATING_PATTERN:
            return False

        # Require at least 2 alternating points for more confidence
        alternating_count = 0
        for i in range(1, len(values) - 1):
            # Check for peak (high-low-high) with significant difference
            if values[i - 1] < values[i] > values[i + 1]:
                # Ensure the difference is significant (at least ALTERNATING_PATTERN_DIFF_THRESHOLD of the range)
                value_range = max(values) - min(values)
                if value_range > 0:
                    diff1 = abs(values[i] - values[i - 1]) / value_range
                    diff2 = abs(values[i] - values[i + 1]) / value_range
                    if (
                        diff1 > self.ALTERNATING_PATTERN_DIFF_THRESHOLD
                        and diff2 > self.ALTERNATING_PATTERN_DIFF_THRESHOLD
                    ):
                        alternating_count += 1
            # Check for valley (low-high-low) with significant difference
            elif values[i - 1] > values[i] < values[i + 1]:
                value_range = max(values) - min(values)
                if value_range > 0:
                    diff1 = abs(values[i - 1] - values[i]) / value_range
                    diff2 = abs(values[i + 1] - values[i]) / value_range
                    if (
                        diff1 > self.ALTERNATING_PATTERN_DIFF_THRESHOLD
                        and diff2 > self.ALTERNATING_PATTERN_DIFF_THRESHOLD
                    ):
                        alternating_count += 1

        # Require at least MIN_ALTERNATING_POINTS alternating points
        return alternating_count >= self.MIN_ALTERNATING_POINTS

    def reset(self):
        """Reset detector state."""
        self._segment_features.clear()

    def get_recent_features(self) -> list[Dict[str, float]]:
        """Get recent segment features for debugging.

        Returns:
            List of recent feature dictionaries
        """
        return list(self._segment_features)

"""What the protocol reports, and the samples it decodes."""

from __future__ import annotations

import enum
from dataclasses import dataclass, field
from typing import Union


class State(enum.Enum):
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    READY = "READY"
    STARTING = "STARTING"
    STREAMING = "STREAMING"
    STOPPING = "STOPPING"
    FAILED = "FAILED"


@dataclass(frozen=True)
class Reading:
    """One channel of one sample. ``uncal`` is the raw value, ``cal`` the calibrated one."""

    cal: float
    units: str
    uncal: float | None = None


@dataclass(frozen=True)
class Sample:
    """One decoded data packet: its channels by name, in packet order."""

    readings: dict[str, Reading] = field(default_factory=dict)

    def __getitem__(self, channel: str) -> float:
        """The calibrated value of a channel, e.g. ``sample["Accel_LN_X"]``."""
        return self.readings[channel].cal


@dataclass(frozen=True)
class StateChanged:
    state: State


@dataclass(frozen=True)
class Initialised:
    """The handshake finished: the device is configured and ready to stream."""

    summary: str


@dataclass(frozen=True)
class Error:
    """The protocol failed, for instance a command timed out."""

    message: str


@dataclass(frozen=True)
class Discarded:
    """Bytes were dropped, or something was ignored; for information."""

    message: str


@dataclass(frozen=True)
class LinkLost:
    """The host reported that the link to the device was lost."""

    reason: str


Event = Union[StateChanged, Initialised, Sample, Error, Discarded, LinkLost]

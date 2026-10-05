"""Runs a Shimmer3RProtocol for an application, so that a transport only moves bytes.

Every call into the protocol happens on the asyncio event loop, which is one thread, so the
protocol needs no lock; a 20 ms ticker drives its timeouts. Writes go out in order through one
writer task, and events queue for the application, so a slow consumer never holds up the bytes
arriving.

A transport needs only this: open the link, call ``connect()``, pass every notification to
``on_bytes()``, report a lost link with ``on_link_lost()``, and provide ``async write(data)``.
Call ``on_bytes()`` and ``on_link_lost()`` on the event loop; bleak already does.

A port of the Java com.shimmerresearch.protocol.ProtocolHost (DEV-1134).
"""

from __future__ import annotations

import asyncio
import time
from typing import Callable, Protocol

from .events import Error, Event, State
from .model import Shimmer3RModel
from .protocol import Output, Shimmer3RProtocol


class Transport(Protocol):
    async def write(self, data: bytes) -> None:
        """Sends one write to the device, without waiting for a response."""


def now_ms() -> int:
    return time.time_ns() // 1_000_000


class ProtocolHost:
    TICK_S = 0.020

    def __init__(self, transport: Transport, clock: Callable[[], int] = now_ms) -> None:
        self._protocol = Shimmer3RProtocol()
        self._transport = transport
        self._clock = clock
        self._writes: asyncio.Queue[bytes] = asyncio.Queue()
        self._tasks: list[asyncio.Task] = []
        self.events: asyncio.Queue[Event] = asyncio.Queue()
        """Everything the protocol reports, in order: states, samples, errors."""

    @property
    def state(self) -> State:
        return self._protocol.state

    @property
    def sampling_rate(self) -> float:
        return self._protocol.sampling_rate

    @property
    def model(self) -> Shimmer3RModel:
        return self._protocol.model

    def connect(self) -> None:
        """Call once the link is open. Starts the handshake, the timeout ticker and the writer."""
        self._tasks = [asyncio.create_task(self._writer()), asyncio.create_task(self._ticker())]
        self._apply(self._protocol.connect(self._clock()))

    def on_bytes(self, data: bytes) -> None:
        """Bytes from the device, in the order they arrived."""
        self._apply(self._protocol.receive(bytes(data), self._clock()))

    def on_link_lost(self, reason: str) -> None:
        self._apply(self._protocol.link_lost(reason, self._clock()))

    def start_streaming(self) -> None:
        self._apply(self._protocol.start_streaming(self._clock()))

    def stop_streaming(self) -> None:
        self._apply(self._protocol.stop_streaming(self._clock()))

    async def close(self) -> None:
        """Stops the ticker and the writer. Does not close the transport."""
        for task in self._tasks:
            task.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks = []

    def _apply(self, out: Output) -> None:
        for data in out.writes:
            self._writes.put_nowait(data)
        for event in out.events:
            self.events.put_nowait(event)

    async def _writer(self) -> None:
        while True:
            data = await self._writes.get()
            try:
                await self._transport.write(data)
            except Exception as e:  # the command's timeout reports the consequence; this says why
                self.events.put_nowait(Error(f"write failed: {e}"))

    async def _ticker(self) -> None:
        while True:
            await asyncio.sleep(self.TICK_S)
            deadline = self._protocol.next_deadline()
            now = self._clock()
            if deadline is not None and now >= deadline:
                self._apply(self._protocol.tick(now))

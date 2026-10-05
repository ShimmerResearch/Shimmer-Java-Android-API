"""The recorded Shimmer3R session, a responder that answers writes from it, and a replay of the
protocol against it on a simulated clock. Ports of the Java test classes RecordedSession,
SessionResponder and ProtocolReplay (DEV-1134)."""

from __future__ import annotations

import csv
import heapq
import itertools
import re
from dataclasses import dataclass, field, replace
from pathlib import Path

from shimmer3r.events import Error, Event, Sample, State
from shimmer3r.protocol import SET_RWC_COMMAND, Output, Shimmer3RProtocol

DATA = Path(__file__).parent / "data"
SESSION = DATA / "shimmer3r_2f31_handshake_stream10s.bytes.log"
JAVA_REFERENCE = DATA / "java_reference.csv"

# The simulated clock's origin, as in the Java ProtocolReplay, so that times line up.
START_MS = 1790960000000

_LINE = re.compile(r"^(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(TX|RX) h\d+ \[([^\]]*)\]")


@dataclass(frozen=True)
class Entry:
    time_ms: int  # since the first TX or RX line
    direction: str  # "TX" (host to device) or "RX"
    data: bytes


def load_session(path: Path = SESSION) -> list[Entry]:
    """One entry per BLE write (TX) or notification (RX); other lines are skipped."""
    entries: list[Entry] = []
    first_ms = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = _LINE.match(line)
        if not m:
            continue
        ms = ((int(m[1]) * 60 + int(m[2])) * 60 + int(m[3])) * 1000 + int(m[4])
        if first_ms is None:
            first_ms = ms
        entries.append(Entry(ms - first_ms, m[5], bytes(int(p, 16) for p in m[6].split())))
    return entries


def split_into_single_bytes(entries: list[Entry]) -> list[Entry]:
    """Every RX entry split into one-byte notifications at the same time."""
    split: list[Entry] = []
    for e in entries:
        if e.direction == "TX":
            split.append(e)
        else:
            split.extend(Entry(e.time_ms, "RX", bytes([b])) for b in e.data)
    return split


def corrupt_rx(entries: list[Entry], n: int, byte_index: int) -> list[Entry]:
    """One byte of the ``n``th RX entry (from 0) XOR-ed with 0xFF."""
    rx = [i for i, e in enumerate(entries) if e.direction == "RX"]
    data = bytearray(entries[rx[n]].data)
    data[byte_index] ^= 0xFF
    copy = list(entries)
    copy[rx[n]] = replace(entries[rx[n]], data=bytes(data))
    return copy


def first_rx_after(entries: list[Entry], opcode: int) -> int:
    """Index, among RX entries, of the first one after the TX entry that starts with ``opcode``."""
    seen = False
    rx = 0
    for e in entries:
        if e.direction == "TX" and e.data and e.data[0] == opcode:
            seen = True
        elif e.direction == "RX":
            if seen:
                return rx
            rx += 1
    return -1


class SessionResponder:
    """Answers each write with the RX entries recorded after the same command."""

    def __init__(self, entries: list[Entry]) -> None:
        self._entries = entries
        self._cursor = 0
        self.unanswered: list[bytes] = []

    def answer(self, written: bytes) -> tuple[int, list[Entry]] | None:
        """(time the command was recorded, the replies), or None if the recording has none."""
        nxt = self._next_tx(self._cursor)
        if nxt >= 0 and self._same_command(self._entries[nxt].data, written):
            tx_time, rx = self._answer_at(nxt)
            self._cursor = nxt + 1 + len(rx)
            return tx_time, rx
        for i, e in enumerate(self._entries):
            if e.direction == "TX" and self._same_command(e.data, written):
                return self._answer_at(i)
        self.unanswered.append(written)
        return None

    @staticmethod
    def _same_command(recorded: bytes, written: bytes) -> bool:
        if not recorded or not written or recorded[0] != written[0]:
            return False
        return written[0] == SET_RWC_COMMAND or recorded == written  # the clock always differs

    def _answer_at(self, tx: int) -> tuple[int, list[Entry]]:
        rx = list(itertools.takewhile(lambda e: e.direction == "RX", self._entries[tx + 1 :]))
        return self._entries[tx].time_ms, rx

    def _next_tx(self, start: int) -> int:
        for i in range(start, len(self._entries)):
            if self._entries[i].direction == "TX":
                return i
        return -1


@dataclass
class Replay:
    """The protocol driven by a recording: each write answered as recorded, delayed as recorded,
    and the clock jumping straight to the next delivery or deadline. Nothing sleeps."""

    responder: SessionResponder
    samples: list[Sample] = field(default_factory=list)
    events: list[Event] = field(default_factory=list)
    written: list[bytes] = field(default_factory=list)
    now: int = START_MS
    _pending: list[tuple[int, int, bytes]] = field(default_factory=list)
    _order: itertools.count = field(default_factory=itertools.count)

    @classmethod
    def run(cls, entries: list[Entry], protocol: Shimmer3RProtocol | None = None) -> "Replay":
        """Connects, starts streaming once ready, and runs until the recording is exhausted."""
        p = protocol or Shimmer3RProtocol()
        r = cls(SessionResponder(entries))
        r._handle(p.connect(r.now))
        started = False
        while p.state is not State.FAILED:
            if not started and p.state is State.READY:
                started = True
                r._handle(p.start_streaming(r.now))
                continue
            next_delivery = r._pending[0][0] if r._pending else None
            deadline = p.next_deadline()
            if next_delivery is None and deadline is None:
                break
            if deadline is None or (next_delivery is not None and next_delivery <= deadline):
                when, _, data = heapq.heappop(r._pending)
                r.now = max(r.now, when)
                r._handle(p.receive(data, r.now))
            else:
                r.now = deadline
                r._handle(p.tick(r.now))
        return r

    def errors(self) -> list[str]:
        return [e.message for e in self.events if isinstance(e, Error)]

    def _handle(self, out: Output) -> None:
        for e in out.events:
            self.events.append(e)
            if isinstance(e, Sample):
                self.samples.append(e)
        for w in out.writes:
            self.written.append(w)
            answer = self.responder.answer(w)
            if answer is None:
                continue
            tx_time, rx = answer
            for e in rx:
                delay = max(0, e.time_ms - tx_time)
                heapq.heappush(self._pending, (self.now + delay, next(self._order), e.data))


def load_java_reference(
    path: Path = JAVA_REFERENCE,
) -> tuple[list[tuple[str, str, str]], list[dict]]:
    """The Java decoder's output for the recording (written by API_00030_PythonReferenceTest):
    the columns as (channel, format, units), and one dict per sample of column -> value."""
    with path.open(encoding="utf-8", newline="") as f:
        rows = list(csv.reader(f))
    header = [tuple(h.split("|")) for h in rows[0][1:]]
    samples = [
        {col: (float(v) if v != "" else None) for col, v in zip(header, row[1:])}
        for row in rows[1:]
    ]
    return header, samples

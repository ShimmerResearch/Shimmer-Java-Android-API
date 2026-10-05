"""ProtocolHost on a real asyncio loop, over a transport that answers from the recorded session as
a radio would. A port of the Java API_00029_ProtocolHostTest."""

import asyncio

from shimmer3r.events import Initialised, LinkLost, Sample, State, StateChanged
from shimmer3r.host import ProtocolHost
from support import Replay, SessionResponder, load_session

WAIT_S = 10


class RecordedDevice:
    """Answers each write from the recording, delivering the replies on the loop as bleak does."""

    def __init__(self) -> None:
        self.responder = SessionResponder(load_session())
        self.host: ProtocolHost | None = None
        self.written: list[bytes] = []

    async def write(self, data: bytes) -> None:
        self.written.append(data)
        answer = self.responder.answer(data)
        if answer is not None:
            for entry in answer[1]:
                asyncio.get_running_loop().call_soon(self.host.on_bytes, entry.data)


async def events_until(host: ProtocolHost, done, collected: list) -> None:
    async def collect():
        while not done(collected):
            collected.append(await host.events.get())

    await asyncio.wait_for(collect(), WAIT_S)


def start() -> tuple[RecordedDevice, ProtocolHost]:
    device = RecordedDevice()
    host = ProtocolHost(device)
    device.host = host
    host.connect()
    return device, host


def test_the_host_streams_the_same_samples_as_the_state_machine_on_its_own():
    reference = Replay.run(load_session()).samples

    async def run():
        device, host = start()
        events = []
        await events_until(host, lambda ev: any(isinstance(e, Initialised) for e in ev), events)
        assert host.state is State.READY
        assert host.sampling_rate == 51.2
        host.start_streaming()
        await events_until(
            host, lambda ev: sum(isinstance(e, Sample) for e in ev) >= len(reference), events
        )
        await host.close()
        return device, host, events

    device, host, events = asyncio.run(run())

    assert host.state is State.STREAMING
    assert device.responder.unanswered == []
    samples = [e for e in events if isinstance(e, Sample)]
    assert len(samples) == len(reference)
    for a, b in zip(reference, samples):
        assert a.readings["Timestamp"] == b.readings["Timestamp"]
        assert a["Accel_LN_X"] == b["Accel_LN_X"]


def test_a_lost_link_is_delivered_and_ends_the_session():
    async def run():
        device, host = start()
        events = []
        await events_until(host, lambda ev: any(isinstance(e, Initialised) for e in ev), events)
        writes_before = len(device.written)
        host.on_link_lost("device switched off")
        host.start_streaming()  # too late: ignored
        await events_until(
            host,
            lambda ev: any(
                isinstance(e, StateChanged) and e.state is State.DISCONNECTED for e in ev
            ),
            events,
        )
        await asyncio.sleep(0.1)
        await host.close()
        return device, host, events, writes_before

    device, host, events, writes_before = asyncio.run(run())

    assert events[-2] == LinkLost("device switched off")
    assert events[-1] == StateChanged(State.DISCONNECTED)
    assert host.events.empty(), "nothing after the link is lost"
    assert len(device.written) == writes_before, "nothing is written after the link is lost"

"""Streams a Shimmer3R over Bluetooth LE for a few seconds and reports what arrived.

python examples/stream.py Shimmer3R-2F31 10
"""

import asyncio
import logging
import math
import sys
import time

from shimmer3r.ble import Shimmer3R


async def main(name: str, seconds: float) -> None:
    started = time.monotonic()
    async with Shimmer3R(name) as device:
        print(
            f"ready in {time.monotonic() - started:.1f} s (scan, connect, handshake): {device.summary}"
        )
        print(f"MTU {device.mtu}")

        samples = []

        async def collect():
            async for sample in device.samples():
                samples.append(sample)

        await device.start_streaming()
        collecting = asyncio.create_task(collect())
        await asyncio.sleep(seconds)
        await device.stop_streaming()
        await collecting

    print(
        f"{len(samples)} samples in {seconds:.0f} s = {len(samples) / seconds:.1f}/s "
        f"at {device.sampling_rate} Hz configured"
    )
    if samples:
        last = samples[-1]
        print(f"reception {last['Packet_Reception_Rate_Trial']:.1f}%")
        for channel, reading in last.readings.items():
            if not channel.startswith("System_Timestamp"):
                print(f"  {channel:30} {reading.cal:14.4f} {reading.units}")
        g = [
            math.sqrt(s["Accel_LN_X"] ** 2 + s["Accel_LN_Y"] ** 2 + s["Accel_LN_Z"] ** 2) / 9.81
            for s in samples
        ]
        print(f"accel magnitude: mean {sum(g) / len(g):.4f} g")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="  %(message)s")
    asyncio.run(
        main(
            sys.argv[1] if len(sys.argv) > 1 else "Shimmer3R",
            float(sys.argv[2]) if len(sys.argv) > 2 else 10,
        )
    )

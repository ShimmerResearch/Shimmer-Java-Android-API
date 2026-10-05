"""The Shimmer UART checksum, as the firmware computes it over replies and data packets.

A port of com.shimmerresearch.comms.wiredProtocol.ShimmerCrc.
"""

_CRC_INIT = 0xB0CA


def _crc_byte(crc: int, b: int) -> int:
    # Bits above 16 never reach the low 16, so Python's unbounded ints give the same result as
    # Java's 32-bit ones once masked.
    crc &= 0xFFFF
    crc = (crc >> 8) | (crc << 8)
    crc ^= b & 0xFF
    crc ^= (crc & 0xFF) >> 4
    crc ^= crc << 12
    crc ^= (crc & 0xFF) << 5
    return crc & 0xFFFF


def crc(data: bytes) -> bytes:
    """The two checksum bytes, LSB first, over all of ``data``."""
    value = _crc_byte(_CRC_INIT, data[0])
    for b in data[1:]:
        value = _crc_byte(value, b)
    if len(data) % 2:
        value = _crc_byte(value, 0x00)
    return bytes((value & 0xFF, value >> 8))

#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
"""Utility classes and functions used for testing polling frame notifications
"""

from dataclasses import dataclass
import time
import functools


class TimedWrapper:
    """Proxies attribute access operation target
    If accessed attribute is callable, wraps the original callable
    into a function which tracks execution time
    """

    def __init__(self, target):
        self._target = target
        self.timings = []

    def __getattr__(self, name):
        attr = getattr(self._target, name)

        if not callable(attr):
            return attr

        @functools.wraps(attr)
        def wrapped_method(*args, **kwargs):
            start_time = time.monotonic_ns()
            result = attr(*args, **kwargs)
            end_time = time.monotonic_ns()

            # Store the timing
            self.timings.append((start_time, end_time))

            return result

        return wrapped_method


@dataclass
class TransceiveConfiguration:
    """Defines settings used during NFC communication
    """
    type: str
    crc: int = True
    bits: int = 8
    bitrate: int = 106
    timeout: float = None


@dataclass
class PollingFrameTestCase:
    """Defines a test case for polling frame tests,
    containing data and transceive configuration to send the frame with
    To verify against lists of expected types and data values
    """
    configuration: TransceiveConfiguration
    data: str

    expected_types: list
    expected_data: list

    def format_for_error(self, **kwargs):
        """Formats testcase value for pretty reporting in errors"""
        extras = {**kwargs}
        if self.configuration.type not in {"O", "X"}:
            extras["crc"] = self.configuration.crc
            extras["bits"] = self.configuration.bits
        if self.configuration.bitrate != 106:
            extras["bitrate"] = self.configuration.bitrate
        return {"type": self.configuration.type, "data": self.data, **extras}


@dataclass
class PollingFrame:
    """Represents PollingFrame object returned from an Android device"""
    type: str
    data: bytes = b""
    timestamp: int = 0
    triggered_auto_transact: bool = False
    vendor_specific_gain: int = 0

    @staticmethod
    def from_dict(json: dict):
        """Creates a PollingFrame object from dict"""
        return PollingFrame(
            type=json.get("type"),
            data=bytes.fromhex(json.get("data")),
            timestamp=json.get("timestamp"),
            triggered_auto_transact=json.get(
                "triggeredAutoTransact", json.get("triggered_auto_transact")
            ),
            vendor_specific_gain=json.get(
                "vendorSpecificGain", json.get("vendor_specific_gain")
            ),
        )

    def to_dict(self):
        """Dumps PollingFrame object into a dict"""
        return {
            "type": self.type,
            "data": self.data.hex().upper(),
            "timestamp": self.timestamp,
            "triggeredAutoTransact": self.triggered_auto_transact,
            "vendorSpecificGain": self.vendor_specific_gain,
        }

    def __repr__(self) -> str:
        return (
            f"{self.__class__.__name__}"
            + f"({', '.join(f'{k}={v}' for k, v in self.to_dict().items())})"
        )


_CARRIER = 13.56e6
_A_TIMEOUT = (1236 + 384) / _CARRIER
_B_TIMEOUT = 7680 / _CARRIER
_F_TIMEOUT = 6800 / _CARRIER


_GUARD_TIME_A = 0.0051
_GUARD_TIME_B = 0.0051
_GUARD_TIME_F = 0.02
_GUARD_TIME = max(_GUARD_TIME_A, _GUARD_TIME_B, _GUARD_TIME_F)
GUARD_TIME_PER_TECH = {
    "O": _GUARD_TIME,
    "X": _GUARD_TIME,
    "A": _GUARD_TIME_A,
    "B": _GUARD_TIME_B,
    "F": _GUARD_TIME_F,
}


# Placeholder values for ON and OFF events
_O = TransceiveConfiguration(type="O")
_X = TransceiveConfiguration(type="X")

# Possible transceive configurations for polling frames
_A = TransceiveConfiguration(
    type="A", crc=True, bits=8, timeout=_A_TIMEOUT
)
_A_SHORT = TransceiveConfiguration(
    type="A", crc=False, bits=7, timeout=_A_TIMEOUT
)
_A_NOCRC = TransceiveConfiguration(
    type="A", crc=False, bits=8, timeout=_A_TIMEOUT
)

_B = TransceiveConfiguration(
    type="B", crc=True, bits=8, timeout=_B_TIMEOUT
)
_B_NOCRC = TransceiveConfiguration(
    type="B", crc=False, bits=8, timeout=_B_TIMEOUT
)

_F = TransceiveConfiguration(
    type="F", crc=True, bits=8, bitrate=212, timeout=_F_TIMEOUT
)
_F_424 = TransceiveConfiguration(
    type="F", crc=True, bits=8, bitrate=424, timeout=_F_TIMEOUT
)


# Possible polling frame configurations
# 1) Frames with special meaning like wakeup/request:
#    - WUPA/REQA WUPB/REQB, SENSF_REQ, etc.
# 2) Special cases:
#    - 7-bit short frames (Type A only);
#    - 424 kbps (Type F only)
# 3) Full frames without CRC (Types A,B only)
# 4) Full frames with CRC (Types A,B,F)

# Placeholder test cases for ON/OFF
POLLING_FRAME_ON = PollingFrameTestCase(_O, "01", ["O"], ["01"])
POLLING_FRAME_OFF = PollingFrameTestCase(_X, "00", ["X"], ["00"])

# Type A
# 1)
POLLING_FRAME_REQA = PollingFrameTestCase(_A_SHORT, "26", ["A"], ["52", ""])
POLLING_FRAME_WUPA = PollingFrameTestCase(_A_SHORT, "52", ["A"], ["26", ""])
POLLING_FRAMES_TYPE_A_SPECIAL = [
    POLLING_FRAME_REQA,
    POLLING_FRAME_WUPA,
]
# 2) 7-bit short frames
POLLING_FRAMES_TYPE_A_SHORT = [
    PollingFrameTestCase(_A_SHORT, "20", ["U"], [""]),
    PollingFrameTestCase(_A_SHORT, "06", ["U"], [""]),
    PollingFrameTestCase(_A_SHORT, "50", ["U"], [""]),
    PollingFrameTestCase(_A_SHORT, "02", ["U"], [""]),
    PollingFrameTestCase(_A_SHORT, "70", ["U"], [""]),
    PollingFrameTestCase(_A_SHORT, "7a", ["U"], [""]),
]
# 3)
POLLING_FRAMES_TYPE_A_NOCRC = [
    PollingFrameTestCase(_A_NOCRC, "aa", ["U"], [""]),
    PollingFrameTestCase(_A_NOCRC, "55aa", ["U"], [""]),
    PollingFrameTestCase(_A_NOCRC, "aa55aa", ["U"], [""]),
    PollingFrameTestCase(_A_NOCRC, "55aa55aa", ["U"], [""]),
]
# 4)
POLLING_FRAMES_TYPE_A_LONG = [
    PollingFrameTestCase(_A, "02f1", ["U"], []),
    PollingFrameTestCase(_A, "ff00", ["U"], []),
    PollingFrameTestCase(_A, "ff001122", ["U"], []),
    PollingFrameTestCase(_A, "ff00112233445566", ["U"], []),
    PollingFrameTestCase(_A, "ff00112233445566778899aa", ["U"], []),
    PollingFrameTestCase(_A, "ff00112233445566778899aabbccddee", ["U"], []),
]

# Type B
# 1)
POLLING_FRAMES_TYPE_B_SPECIAL = [
    # 1.1) Common cases
    #   REQB, AFI 0x00, TS 0x00
    PollingFrameTestCase(_B, "050000", ["B"], []),
    #   WUPB, AFI 0x00, TS 0x00
    PollingFrameTestCase(_B, "050008", ["B"], []),
    # 1.2) Different AFI values
    #   REQB, AFI 0x01, TS 0x00
    PollingFrameTestCase(_B, "050100", ["B"], []),
    #   WUPB, AFI 0x02, TS 0x00
    PollingFrameTestCase(_B, "050208", ["B"], []),
    # 1.3) Different Timeslot counts
    #   REQB, AFI 0x00, TS 0x01 (2)
    PollingFrameTestCase(_B, "050001", ["B"], []),
    #   WUPB, AFI 0x00, TS 0x02 (4)
    PollingFrameTestCase(_B, "05000a", ["B"], []),
]
# 3)
POLLING_FRAMES_TYPE_B_NOCRC = [
    PollingFrameTestCase(_B_NOCRC, "aa", ["U"], [""]),
    PollingFrameTestCase(_B_NOCRC, "55aa", ["U"], [""]),
    PollingFrameTestCase(_B_NOCRC, "aa55aa", ["U"], [""]),
    PollingFrameTestCase(_B_NOCRC, "55aa55aa", ["U"], [""]),
]
# 4)
POLLING_FRAMES_TYPE_B_LONG = [
    PollingFrameTestCase(_B, "02f1", ["U"], []),
    # 2 bytes
    PollingFrameTestCase(_B, "ff00", ["U"], []),
    # 4 bytes
    PollingFrameTestCase(_B, "ff001122", ["U"], []),
    # 8 bytes
    PollingFrameTestCase(_B, "ff00112233445566", ["U"], []),
    # 12 bytes
    PollingFrameTestCase(_B, "ff00112233445566778899aa", ["U"], []),
    # 16 bytes
    PollingFrameTestCase(_B, "ff00112233445566778899aabbccddee", ["U"], []),
]

# Type F
# 1/2)
POLLING_FRAMES_TYPE_F_SPECIAL = [
    # 1.0) Common
    #   SENSF_REQ, SC, 0xffff, RC 0x00, TS 0x00
    PollingFrameTestCase(_F, "00ffff0000", ["F"], []),
    #   SENSF_REQ, SC, 0x0003, RC 0x00, TS 0x00
    PollingFrameTestCase(_F, "0000030000", ["F"], []),
    # 1.1) Different request codes
    #   SENSF_REQ, SC, 0xffff, RC 0x01, TS 0x00
    PollingFrameTestCase(_F, "00ffff0100", ["F"], []),
    #   SENSF_REQ, SC, 0x0003, RC 0x01, TS 0x00
    PollingFrameTestCase(_F, "0000030100", ["F"], []),
    # 1.2) Different Timeslot counts
    #   SENSF_REQ, SC, 0xffff, RC 0x00, TS 0x01 (2)
    PollingFrameTestCase(_F, "00ffff0001", ["F"], []),
    #   SENSF_REQ, SC, 0x0003, RC 0x00, TS 0x02 (4)
    PollingFrameTestCase(_F, "0000030002", ["F"], []),
    # 2) 424 kbps
    #   SENSF_REQ, SC, 0xffff
    PollingFrameTestCase(_F_424, "00ffff0100", ["F"], []),
    #   SENSF_REQ, SC, 0x0003
    PollingFrameTestCase(_F_424, "00ffff0100", ["F"], []),
]
# 4)
POLLING_FRAMES_TYPE_F_LONG = [
    PollingFrameTestCase(_F, "ffaabbccdd", ["U"], []),
    PollingFrameTestCase(_F, "ff00112233", ["U"], []),
    # 2 bytes
    PollingFrameTestCase(_F, "ff00", ["U"], []),
    # 4 bytes
    PollingFrameTestCase(_F, "ff001122", ["U"], []),
    # 8 bytes
    PollingFrameTestCase(_F, "ff00112233445566", ["U"], []),
    # 12 bytes
    PollingFrameTestCase(_F, "ff00112233445566778899aa", ["U"], []),
    # 16 bytes
    PollingFrameTestCase(_F, "ff00112233445566778899aabbccddee", ["U"], []),
]


POLLING_FRAME_ALL_TEST_CASES = [
    POLLING_FRAME_ON,
    *POLLING_FRAMES_TYPE_A_SPECIAL,
    *POLLING_FRAMES_TYPE_A_SHORT,
    *POLLING_FRAMES_TYPE_A_NOCRC,
    *POLLING_FRAMES_TYPE_A_LONG,
    *POLLING_FRAMES_TYPE_B_SPECIAL,
    *POLLING_FRAMES_TYPE_B_NOCRC,
    *POLLING_FRAMES_TYPE_B_LONG,
    *POLLING_FRAMES_TYPE_F_SPECIAL,
    *POLLING_FRAMES_TYPE_F_LONG,
    POLLING_FRAME_OFF,
]


EXPEDITED_POLLING_LOOP_EVENT_TYPES = ["F", "U"]


def get_expedited_frames(frames):
    """Finds and collects all expedited polling frames.
    Expedited frames belong to F, U types and they get reported
    to the service while the OS might still be evaluating the loop
    """
    expedited_frames = []
    # Expedited frames come at the beginning
    for frame in frames:
        if frame.type not in EXPEDITED_POLLING_LOOP_EVENT_TYPES:
            break
        expedited_frames.append(frame)
    return expedited_frames


def split_frames_by_timestamp_wrap(frames, pivot_timestamp=None):
    """Returns two lists of polling frames
    split based on the timestamp value wrapping over to lower value
    assuming that frames were provided in the way they arrived
    """
    if not frames:
        return [], []
    # Take the first timestamp from first frame (or the one provided)
    # And check that timestamp for all frames that come afterwards is bigger
    # otherwise consider them wrapped
    pivot_timestamp = pivot_timestamp or frames[0].timestamp
    not_wrapped = []
    for frame in frames:
        if frame.timestamp < pivot_timestamp:
            break
        not_wrapped.append(frame)
    wrapped = frames[len(not_wrapped) :]
    return not_wrapped, wrapped


def apply_expedited_frame_ordering(frames, limit=3):
    """Attempts to replicate expedited frame delivery behavior
    of HostEmulationManager for type F, U events
    """
    leave, expedite = [], []

    for frame in frames:
        if frame.type in EXPEDITED_POLLING_LOOP_EVENT_TYPES \
            and len(expedite) < limit:
            expedite.append(frame)
        else:
            leave.append(frame)
    return expedite + leave


def apply_original_frame_ordering(frames):
    """Reverts expedited frame ordering caused by HostEmulationManager,
    useful when having the original polling frame order is preferable in a test

    Call this function ONLY with a list of frames resembling a full polling loop
    with possible expedited F, U events at the beginning.
    """
    if len(frames) == 0:
        return []

    expedited_frames = get_expedited_frames(frames)
    # If no expedited frames were found at the beginning, leave
    if len(expedited_frames) == 0:
        return frames

    # Original frames come after expedited ones
    original_frames = frames[len(expedited_frames) :]

    # In between expedited and original frames,
    # which should be pre-sorted in their category
    # there might be a timestamp wrap
    original_not_wrapped, original_wrapped = split_frames_by_timestamp_wrap(
        original_frames
    )
    # Non-expedited, original frame should be the first one in the loop
    # so we can use the timestamp of the first expedited frame as a pivot
    expedited_not_wrapped, expedited_wrapped = split_frames_by_timestamp_wrap(
        expedited_frames,
        pivot_timestamp=(
            original_not_wrapped[0].timestamp
            if len(original_not_wrapped) > 0 else None
        ),
    )

    return sorted(
        original_not_wrapped + expedited_not_wrapped, key=lambda f: f.timestamp
    ) + sorted(original_wrapped + expedited_wrapped, key=lambda f: f.timestamp)


def _test_apply_original_frame_ordering():
    """Verifies that 'apply_original_frame_ordering' works properly"""
    testcases = [
        # Overflow after Normal B
        (
            ("O", 4), ("A", 5), ("U", 6), ("B", 7),
            ("U", 0), ("F", 1), ("U", 2), ("X", 3)
        ),
        # Overflow after Expeditable
        (
            ("O", 4), ("A", 5), ("U", 6), ("B", 7),
            ("U", 8), ("F", 0), ("U", 1), ("X", 2)
        ),
        # Overflow after Normal O
        (("O", 4), ("A", 0), ("B", 1), ("F", 2), ("X", 3)),
        # Overflow after Normal A
        (("O", 4), ("A", 5), ("B", 0), ("F", 1), ("X", 2)),
        # Overflow after Expeditable U
        (("O", 4), ("U", 5), ("A", 0), ("B", 1), ("F", 2), ("X", 3)),
        # No overflow
        (("O", 0), ("A", 1), ("B", 2), ("X", 3)),
        # No overflow
        (("O", 0), ("A", 1), ("B", 2), ("F", 3), ("X", 4)),
        # No overflow
        (("O", 0), ("A", 1), ("U", 2), ("B", 3), ("U", 4), ("F", 5), ("X", 6)),
    ]

    for testcase in testcases:
        original_frames = [
            PollingFrame(type_, b"", timestamp)
            for (type_, timestamp) in testcase
        ]
        # Test for case where none or all frames get expedited
        for limit in range(len(original_frames)):
            expedited_frames = apply_expedited_frame_ordering(
                original_frames, limit=limit
            )
            restored_frames = apply_original_frame_ordering(expedited_frames)
            assert original_frames == restored_frames


# This should not raise anything when module is imported
_test_apply_original_frame_ordering()


# Time conversion
def ns_to_ms(t):
    """Converts nanoseconds (10^−9) to milliseconds (10^−3)"""
    return t / 1000000


def ns_to_us(t):
    """Converts nanoseconds (10^−9) to microseconds (10^−6)"""
    return t / 1000


def us_to_ms(t):
    """Converts microseconds (10^−6) to milliseconds (10^−3)"""
    return t / 1000

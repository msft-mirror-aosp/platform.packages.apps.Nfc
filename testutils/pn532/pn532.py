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

import logging
import serial
import struct
from enum import IntEnum
from . import tag
from binascii import hexlify
from serial.tools.list_ports import comports
from mobly import logger as mobly_logger


_LONG_PREAMBLE = bytearray(20)

_BITRATE = {106: 0b000, 212: 0b001, 424: 0b010, 848: 0b011}
# Framing values defined in PN532_C1, 8.6.23
_FRAMING = {"A": 0b00, "DEP": 0b01, "F": 0b10, "B": 0b11}
# Timeout values defined in UM0701-02, Table 17, from 100 µs (n=1) up to 3.28 sec (n=16)
_TIMEOUT = {n: 100 * 2 ** (n - 1) for n in range(0x01, 0x10)}


def crc16a(data):
    w_crc = 0x6363
    for byte in data:
        byte = byte ^ (w_crc & 0x00FF)
        byte = (byte ^ (byte << 4)) & 0xFF
        w_crc = ((w_crc >> 8) ^ (byte << 8) ^ (byte << 3) ^ (byte >> 4)) & 0xFFFF
    return bytes([w_crc & 0xFF, (w_crc >> 8) & 0xFF])


def with_crc16a(data):
    return bytes(data) + crc16a(data)


class Command(IntEnum):
    """
    https://www.nxp.com/docs/en/user-guide/141520.pdf
    UM0701-02
    """
    DIAGNOSE = 0x00
    GET_FIRMWARE_VERSION = 0x02
    GET_GENERAL_STATUS = 0x04

    READ_REGISTER = 0x06
    WRITE_REGISTER = 0x08

    SAM_CONFIGURATION = 0x14
    POWER_DOWN = 0x16

    RF_CONFIGURATION = 0x32

    IN_JUMP_FOR_DEP = 0x56
    IN_JUMP_FOR_PSL = 0x46
    IN_LIST_PASSIVE_TARGET = 0x4A

    IN_DATA_EXCHANGE = 0x40
    IN_COMMUNICATE_THRU = 0x42

    IN_DESELECT = 0x44
    IN_RELEASE = 0x52
    IN_SELECT = 0x54

    IN_AUTO_POLL = 0x60

    TG_INIT_AS_TARGET = 0x8C
    TG_SET_GENERAL_BYTES = 0x92
    TG_GET_DATA = 0x86
    TG_SET_DATA = 0x8E
    TG_SET_METADATA = 0x94
    TG_GET_INITIATOR_COMMAND = 0x88
    TG_RESPONSE_TO_INITIATOR = 0x90
    TG_GET_TARGET_STATUS = 0x8A


class Register(IntEnum):
    """
    https://www.nxp.com/docs/en/nxp/data-sheets/PN532_C1.pdf
    PN532/C1
    8.6.22 CIU memory map
    8.7.1  Standard registers
    """
    COMMAND        = 0x6331
    COMM_I_EN      = 0x6332
    DIV_I_EN       = 0x6333
    COMM_I_RQ      = 0x6334
    DIV_I_RQ       = 0x6335
    ERROR          = 0x6336
    WATER_LEVEL    = 0x633B
    CONTROL        = 0x633C
    BIT_FRAMING    = 0x633D
    COLL           = 0x633E
    MODE           = 0x6301
    TX_MODE        = 0x6302
    RX_MODE        = 0x6303
    TX_CONTROL     = 0x6304
    TX_AUTO        = 0x6305
    TX_SEL         = 0x6306
    RX_SEL         = 0x6307
    RX_THRESHOLD   = 0x6308
    DEMOD          = 0x6309
    MANUAL_RCV     = 0x630D
    TYPE_B         = 0x630E
    GS_N_OFF       = 0x6313
    MOD_WIDTH      = 0x6314
    TX_BIT_PHASE   = 0x6315
    RF_CFG         = 0x6316
    GS_N_ON        = 0x6317
    CWG_S_P        = 0x6318
    MOD_GS_P       = 0x6319


REG = Register


class RFConfigItem(IntEnum):
    """
    https://www.nxp.com/docs/en/user-guide/141520.pdf
    UM0701-02
    7.3.1 RFConfiguration
    """
    RF_FIELD        = 0x01 # ConfigurationData
    VARIOUS_TIMINGS = 0x02 # RFU, fATR_RES_Timeout, fRetryTimeout
    # 0x03 RFU
    MAX_RTY_COM     = 0x04 # MaxRtyCOM
    MAX_RETRIES     = 0x05 # MxRtyATR, MxRtyPSL, MxRtyPassiveActivation


_REGISTER_CONFIGURATION_FOR_TYPE = {
    # https://www.nxp.com/docs/en/user-guide/141520.pdf
    # UM0701-02
    # Page 102. Analog settings

    # The following entries are based on register changes when performing inListPassiveTarget
    # with different target types

    # OFF -> A
    #  CONTROL        : 00000000 -> 00010000
    #  TX_CONTROL     : 10000000 -> 10000011
    #  RX_THRESHOLD   : 10000100 -> 10000101
    #  GS_N_OFF       : 10001000 -> 01101111
    #  RF_CFG         : 01001000 -> 01011001
    #  GS_N_ON        : 10001000 -> 11110100
    #  CWG_S_P        : 00100000 -> 00111111
    #  MOD_GS_P       : 00100000 -> 00010001
    #
    # B -> A
    #  GS_N_ON        : 11111111 -> 11110100
    #  MOD_GS_P       : 00010111 -> 00010001
    #
    # F -> A
    #  DEMOD          : 01000001 -> 01001101
    #  TX_BIT_PHASE   : 10001111 -> 10000111
    #  RF_CFG         : 01101001 -> 01011001
    #  GS_N_ON        : 11111111 -> 11110100
    "A": {
        REG.CONTROL:      0b00010000,
        REG.TX_CONTROL:   0b10000011,
        REG.RX_THRESHOLD: 0b10000101,
        REG.DEMOD:        0b01001101,
        REG.GS_N_OFF:     0b01101111,
        REG.TX_BIT_PHASE: 0b10000111,
        REG.RF_CFG:       0b01011001,
        REG.GS_N_ON:      0b11110100,
        REG.CWG_S_P:      0b00111111,
        REG.MOD_GS_P:     0b00010001,
    },

    # OFF -> B
    #  CONTROL        : 00000000 -> 00010000
    #  TX_CONTROL     : 10000000 -> 10000011
    #  RX_THRESHOLD   : 10000100 -> 10000101
    #  GS_N_OFF       : 10001000 -> 01101111
    #  RF_CFG         : 01001000 -> 01011001
    #  GS_N_ON        : 10001000 -> 11111111
    #  CWG_S_P        : 00100000 -> 00111111
    #  MOD_GS_P       : 00100000 -> 00010111
    #
    # A -> B
    #  GS_N_ON        : 11110100 -> 11111111
    #  MOD_GS_P       : 00010001 -> 00010111
    #
    # F -> B
    #  DEMOD          : 01000001 -> 01001101
    #  TX_BIT_PHASE   : 10001111 -> 10000111
    #  RF_CFG         : 01101001 -> 01011001
    #  MOD_GS_P       : 00010001 -> 00010111
    "B": {
        REG.CONTROL:      0b00010000,
        REG.TX_CONTROL:   0b10000011,
        REG.RX_THRESHOLD: 0b10000101,
        REG.DEMOD:        0b01001101,
        REG.GS_N_OFF:     0b01101111,
        REG.RF_CFG:       0b01011001,
        REG.GS_N_ON:      0b11111111,
        REG.CWG_S_P:      0b00111111,
        REG.MOD_GS_P:     0b00010111,
    },

    # OFF -> F
    #  CONTROL        : 00000000 -> 00010000
    #  TX_CONTROL     : 10000000 -> 10000011
    #  RX_THRESHOLD   : 10000100 -> 10000101
    #  DEMOD          : 01001101 -> 01000001
    #  GS_N_OFF       : 10001000 -> 01101111
    #  TX_BIT_PHASE   : 10000111 -> 10001111
    #  RF_CFG         : 01001000 -> 01101001
    #  GS_N_ON        : 10001000 -> 11111111
    #  CWG_S_P        : 00100000 -> 00111111
    #  MOD_GS_P       : 00100000 -> 00010001
    #
    # A -> F
    #  DEMOD          : 01001101 -> 01000001
    #  TX_BIT_PHASE   : 10000111 -> 10001111
    #  RF_CFG         : 01011001 -> 01101001
    #  GS_N_ON        : 11110100 -> 11111111
    "F": {
        REG.CONTROL:      0b00010000,
        REG.TX_CONTROL:   0b10000011,
        REG.RX_THRESHOLD: 0b10000101,
        REG.DEMOD:        0b01000001,
        REG.GS_N_OFF:     0b01101111,
        REG.TX_BIT_PHASE: 0b10001111,
        REG.RF_CFG:       0b01101001,
        REG.GS_N_ON:      0b11111111,
        REG.CWG_S_P:      0b00111111,
        REG.MOD_GS_P:     0b00010001,
    }
}


class PN532:

    def __init__(self, path):
        """Initializes device on path, or first available serial port if none is provided."""
        if len(comports()) == 0:
            raise IndexError(
                "Could not find device on serial port, make sure reader is plugged in."
            )
        if len(path) == 0:
            path = comports()[0].device

        self.register_cache = {}
        self.rf_configuration_cache = {}
        self.log = mobly_logger.PrefixLoggerAdapter(
            logging.getLogger(),
            {
                mobly_logger.PrefixLoggerAdapter.EXTRA_KEY_LOG_PREFIX: (
                    f"[PN532|{path}]"
                )
            },
        )
        self.log.debug("Serial port: %s", path)
        self.device = serial.Serial(path, 115200, timeout=0.5)

        self.device.flush()
        self.device.write(_LONG_PREAMBLE + bytearray.fromhex("0000ff00ff00"))
        self.device.flushInput()
        if not self.verify_firmware_version():
            raise RuntimeError("Could not verify PN532 firmware on serial path " + path)
        rsp = self.send_frame(
            _LONG_PREAMBLE + self.construct_frame([Command.SAM_CONFIGURATION, 0x01, 0x00]),
            1,
            )
        if not rsp:
            raise RuntimeError("No response for SAM configuration.")

        # Disable retries
        self.device.flushInput()
        rsp = self.send_frame(
            self.construct_frame(
                [
                    Command.RF_CONFIGURATION,
                    0x05,
                    0x00,  # MxRtyATR
                    0x00,  # MxRtyPSL
                    0x00,  # MxRtyPassiveActivation
                ]
            ),
            1,
        )
        if not rsp:
            raise RuntimeError("No response for RF configuration.")

    def verify_firmware_version(self):
        """Verifies we are talking to a PN532."""
        self.log.debug("Checking firmware version")
        rsp = self.send_frame(
            _LONG_PREAMBLE + self.construct_frame([Command.GET_FIRMWARE_VERSION])
        )

        if not rsp:
            raise RuntimeError("No response for GetFirmwareVersion")

        if rsp[0] != Command.GET_FIRMWARE_VERSION + 1 or len(rsp) != 5:
            self.log.error("Got unexpected response for GetFirmwareVersion")
            return False

        return rsp[1] == 0x32

    def poll_a(self):
        """Attempts to detect target for NFC type A."""
        self.log.debug("Polling A")
        rsp = self.send_frame(
            self.construct_frame([Command.IN_LIST_PASSIVE_TARGET, 0x01, 0x00])
        )
        if not rsp:
            raise RuntimeError("No response for send poll_a frame.")

        if rsp[0] != Command.IN_LIST_PASSIVE_TARGET + 1:
            self.log.error("Got unexpected command code in response")
        del rsp[0]

        num_targets = rsp[0]
        if num_targets == 0:
            return None
        del rsp[0]

        target_id = rsp[0]
        del rsp[0]

        sense_res = rsp[0:2]
        del rsp[0:2]

        sel_res = rsp[0]
        self.log.debug("Got tag, SEL_RES is %02x", sel_res)
        del rsp[0]

        nfcid_len = rsp[0]
        del rsp[0]
        nfcid = rsp[0:nfcid_len]
        del rsp[0:nfcid_len]

        ats_len = rsp[0]
        del rsp[0]
        ats = rsp[0 : ats_len - 1]
        del rsp[0 : ats_len - 1]

        return tag.TypeATag(self, target_id, sense_res, sel_res, nfcid, ats)

    def initialize_target_mode(self):
        """Configures the PN532 as target."""
        self.log.debug("Initializing target mode")
        self.send_frame(
            self.construct_frame([Command.TG_INIT_AS_TARGET,
                                  0x05, #Mode
                                  0x04, #SENS_RES (2 bytes)
                                  0x00,
                                  0x12, #nfcid1T (3 BYTES)
                                  0x34,
                                  0x56,
                                  0x20, #SEL_RES
                                  0x00, #FeliCAParams[] (18 bytes)
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,#NFCID3T[] (10 bytes)
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00,
                                  0x00, #LEN Gt
                                  0x00, #LEN Tk
                                  ]))

    def poll_b(self):
        """Attempts to detect target for NFC type B."""
        self.log.debug("Polling B")
        rsp = self.send_frame(
            self.construct_frame([Command.IN_LIST_PASSIVE_TARGET, 0x01, 0x03, 0x00])
        )
        if not rsp:
            raise RuntimeError("No response for send poll_b frame.")

        if rsp[0] != Command.IN_LIST_PASSIVE_TARGET + 1:
            self.log.error("Got unexpected command code in response")
        del rsp[0]

        afi = rsp[0]

        deselect_command = 0xC2
        self.send_broadcast(bytearray(deselect_command))

        wupb_command = [0x05, afi, 0x08]
        self.send_frame(
            self.construct_frame([Command.WRITE_REGISTER, 0x63, 0x3D, 0x00])
        )
        rsp = self.send_frame(
            self.construct_frame(
                [Command.IN_COMMUNICATE_THRU] + list(with_crc16a(wupb_command))
            )
        )
        if not rsp:
            raise RuntimeError("No response for WUPB command")

        return tag.TypeBTag(self, 0x03, rsp)

    def send_broadcast(self, broadcast):
        """Emits broadcast frame with CRC. This should be called after poll_a()."""
        self.log.debug("Sending broadcast %s", hexlify(broadcast).decode())

        # Adjust bit framing so all bytes are transmitted
        self.send_frame(self.construct_frame([Command.WRITE_REGISTER, 0x63, 0x3D, 0x00]))
        rsp = self.send_frame(
            self.construct_frame([Command.IN_COMMUNICATE_THRU] + list(with_crc16a(broadcast)))
        )
        if not rsp:
            raise RuntimeError("No response for send broadcast.")

    def read_registers(self, *registers, cache=False):
        """
        Reads CIU registers
         :param registers: an iterable containing addresses of registers to read
         :param cache: prevents redundant register reads (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        if cache and all(Register(register) in self.register_cache for register in registers):
            return [self.register_cache[register] for register in registers]
        data = b''.join(struct.pack(">H", register) for register in registers)
        rsp = self.execute_command(Command.READ_REGISTER, data)
        if not rsp:
            raise RuntimeError(f"No response for read registers {args}.")
        return list(rsp)

    def write_registers(self, registers: dict, cache=False) -> None:
        """
        (7.2.5) WriteRegister:
        Writes CIU registers
         :param registers: dictionary containing key-value pairs
         of register addresses and values to be written
         :param cache: prevents redundant register writes (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        # If not caching, assume all are different
        difference = {
            reg: val for reg, val in registers.items()
            if not cache or self.register_cache.get(reg) != val
        }
        if not difference:
            return
        data = b''.join(struct.pack(">HB", reg, val) for reg, val in difference.items())
        self.execute_command(Command.WRITE_REGISTER, data)
        self.register_cache = {**self.register_cache, **registers}

    def rf_configuration(self, cfg_item, value, *, cache=False):
        """
        (7.3.1) RFConfiguration
        Applies settings to one of the available configuration items
        :param cache: prevents redundant config writes (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        if cache and self.rf_configuration_cache.get(cfg_item) == value:
            return
        self.execute_command(Command.RF_CONFIGURATION, [cfg_item, *value])
        self.rf_configuration_cache[cfg_item] = value

    def transceive_raw(
        self,
        data,
        type_="A",
        crc=True,
        bits=8,
        bitrate=106,
        *,
        timeout=1,
        cache_configuration=True
    ):
        """
        Configures the CIU with specified configuration and sends raw data
        :param timeout: Timeout in seconds
        :param cache_configuration: if true, prevents redundant register write and read commands
        """
        # Choose the least index of timeout duration where result >= given value. Timeout is in μs.
        # If timeout value is too big, or <= 0, fall back to maximum timeout duration
        timeout_index = next((idx for idx, t in _TIMEOUT.items() if t >= timeout * 1000000), 0x10)
        self.rf_configuration(
            RFConfigItem.VARIOUS_TIMINGS,
            [
                0x00, # RFU
                0x0B, # ATR_RES TimeOut, default value is 0x0B
                timeout_index
            ],
            cache=cache_configuration
        )

        tx_mode, rx_mode, tx_auto, bit_frm = self.read_registers(
            REG.TX_MODE, REG.RX_MODE, REG.TX_AUTO, REG.BIT_FRAMING,
            cache=cache_configuration
        )

        # (8.6.23.18, 8.6.23.19) Tx/RxMode
        # > Speed                vvv
        tx_mode = (tx_mode & 0b1_000_1_1_11) | (_BITRATE[bitrate] << 4)
        rx_mode = (rx_mode & 0b1_000_1_1_11) | (_BITRATE[bitrate] << 4)
        # > Framing                      vv
        tx_mode = (tx_mode & 0b1_111_1_1_00) | (_FRAMING[type_])
        rx_mode = (rx_mode & 0b1_111_1_1_00) | (_FRAMING[type_])
        # > CRCEn              v
        tx_mode = (tx_mode & 0b0_111_1_1_11) | (crc << 7)
        rx_mode = (rx_mode & 0b0_111_1_1_11) | (crc << 7)
        # Force 100% ASK for Type A (8.6.3.1). Type A does not work without this.
        # (8.6.23.21) TxAuto
        # > Force100ASK          v
        tx_auto = (tx_auto & 0b1_0_1_1_1_1_1_1) | ((type_ == "A") << 6)
        # (8.6.23.15) BitFraming
        # > TxLastbits                 vvv
        bit_frm = (bit_frm & 0b1_111_1_000) | (bits & 0b111)

        self.write_registers(
            {
                REG.TX_MODE: tx_mode,
                REG.RX_MODE: rx_mode,
                REG.TX_AUTO: tx_auto,
                REG.BIT_FRAMING: bit_frm,
                **_REGISTER_CONFIGURATION_FOR_TYPE[type_]
            },
            cache=cache_configuration
        )

        # Handle a special case for FeliCa, where length byte has to be prepended
        if type_ == "F":
            data = [len(data) + 1, *data]

        rsp = self.execute_command(Command.IN_COMMUNICATE_THRU, data)
        if rsp[0] != 0:
            # No data is OK for this use case
            return None
        del rsp[0]

        return rsp

    def transceive(self, data):
        """Sends data to device and returns response."""
        self.log.debug("Transceive")
        rsp = self.send_frame(self.construct_frame([Command.IN_DATA_EXCHANGE] + list(data)), 5)

        if not rsp:
            return None

        if rsp[0] != Command.IN_DATA_EXCHANGE + 1:
            self.log.error("Got unexpected command code in response")
        del rsp[0]

        if rsp[0] != 0:
            self.log.error("Got error exchanging data")
            return None
        del rsp[0]

        return rsp

    def mute(self):
        """Turns off device's RF antenna."""
        self.log.debug("Muting")
        self.rf_configuration(RFConfigItem.RF_FIELD, [0x02])

    def unmute(self):
        """Turns on device's RF antenna."""
        self.log.debug("Unmuting")
        self.rf_configuration(RFConfigItem.RF_FIELD, [0x03])

    def execute_command(self, command, data=b'', timeout=0.5):
        rsp = self.send_frame(self.construct_frame([command, *data]), timeout=timeout)
        if not rsp:
            return None
        if rsp[0] != command + 1:
            raise RuntimeError(f"Response code {rsp[0]} does not match the command {command}")
        del rsp[0]
        return rsp

    def construct_frame(self, data):
        """Construct a data fram to be sent to the PN532."""
        # Preamble, start code, length, length checksum, TFI
        frame = [
            0x00,
            0x00,
            0xFF,
            (len(data) + 1) & 0xFF,
            ((~(len(data) + 1) & 0xFF) + 0x01) & 0xFF,
            0xD4,
            ]
        data_sum = 0xD4

        # Add data to frame
        for b in data:
            data_sum += b
            frame.append(b)
        frame.append(((~data_sum & 0xFF) + 0x01) & 0xFF)  # Data checksum

        frame.append(0x00)  # Postamble
        self.log.debug("Constructed frame " + hexlify(bytearray(frame)).decode())

        return bytearray(frame)

    def send_frame(self, frame, timeout=0.5):
        """
        Writes a frame to the device and returns the response.
        """
        self.device.write(frame)
        return self.get_device_response(timeout)

    def reset_buffers(self):
        self.device.reset_input_buffer()
        self.device.reset_output_buffer()

    def get_device_response(self, timeout=0.5):
        """
        Confirms we get an ACK frame from device, reads response frame, and writes ACK.
        """
        self.device.timeout = timeout
        frame = bytearray(self.device.read(6))

        if (len(frame)) == 0:
            self.log.error("Did not get response from PN532")
            return None

        if hexlify(frame).decode() != "0000ff00ff00":
            self.log.error("Did not get ACK frame, got %s", hexlify(frame).decode())

        frame = bytearray(self.device.read(6))

        if (len(frame)) == 0:
            return None

        if hexlify(frame[0:3]).decode() != "0000ff":
            self.log.error(
                "Unexpected start to frame, got %s", hexlify(frame[0:3]).decode()
            )

        data_len = frame[3]
        length_checksum = frame[4]
        if (length_checksum + data_len) & 0xFF != 0:
            self.log.error("Frame failed length checksum")
            return None

        tfi = frame[5]
        if tfi != 0xD5:
            self.log.error(
                "Unexpected TFI byte when performing read, got %02x", frame[5]
            )
            return None

        data_packet = bytearray(
            self.device.read(data_len - 1)
        )  # subtract one since length includes TFI byte.
        data_checksum = bytearray(self.device.read(1))[0]
        if (tfi + sum(data_packet) + data_checksum) & 0xFF != 0:
            self.log.error("Frame failed data checksum")

        postamble = bytearray(self.device.read(1))[0]
        if postamble != 0x00:
            if tfi != 0xD5:
                self.log.error(
                    "Unexpected postamble byte when performing read, got %02x", frame[4]
                )

        self.device.timeout = 0.5
        self.device.write(
            bytearray.fromhex("0000ff00ff00")
        )  # send ACK frame, there is no response.

        self.log.debug(
            "Received frame %s%s",
            hexlify(frame).decode(),
            hexlify(data_packet).decode(),
        )

        return data_packet

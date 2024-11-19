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
import http
from urllib.parse import urlparse
from http.client import HTTPSConnection
import ssl
import json

def crc16a(data):
    w_crc = 0x6363
    for byte in data:
        byte = byte ^ (w_crc & 0x00FF)
        byte = (byte ^ (byte << 4)) & 0xFF
        w_crc = ((w_crc >> 8) ^ (byte << 8) ^ (byte << 3) ^ (byte >> 4)) & 0xFFFF
    return bytes([w_crc & 0xFF, (w_crc >> 8) & 0xFF])


def with_crc16a(data):
    return bytes(data) + crc16a(data)



def responses_match(expected: bytes, actual: bytes) -> bool:
    if expected == actual:
        return True
    if expected is None or actual is None:
        return False
    if len(expected) == 0 or len(actual) == 0:
        return False
    if expected[0] != 0x00 and actual[0] == 0x00:
        if expected == actual[1:]:
            return True
    return False


class CasimirTag(tag.Tag):
    def __init__(self, casimir, sender_id):
        """Empty init"""
        self.casimir = casimir
        self.sender_id = sender_id
        self.sel_res = 0x60
        self.ats = [0x70, 0x80, 0x08, 0x00]
        self.log = casimir.log

    def transact(self, command_apdus, expected_response_apdus):
        response_apdus = self.casimir.transceive_multiple(self.sender_id, command_apdus)
        for i in range(len(expected_response_apdus)):
            if expected_response_apdus[i] != "*" and len(response_apdus) > i and not responses_match(expected_response_apdus[i], response_apdus[i]):
                received_apdu = hexlify(response_apdus[i]).decode() if type(response_apdus[i]) is bytes else "None"
                self.log.error(
                    "Unexpected APDU: received %s, expected %s",
                    received_apdu,
                    hexlify(expected_response_apdus[i]).decode(),
                )
                return False
        return True

class Casimir:

    def __init__(self, id):
        """ Init """
        self.id = id
        self.host = 'localhost'
        self.conn = None
        self.rf_on = False
        self.log = mobly_logger.PrefixLoggerAdapter(
            logging.getLogger(),
            {
                mobly_logger.PrefixLoggerAdapter.EXTRA_KEY_LOG_PREFIX: (
                    f"[Casimir|{id}]"
                )
            },
        )

    def __del__(self):
        self.mute()

    def verify_firmware_version(self):
        return True

    def poll_a(self):
        """Attempts to detect target for NFC type A."""
        sender_id = self.send_command("PollA", {})
        self.log.debug("got sender_id: " + str(sender_id))
        if sender_id is None:
            return None
        return CasimirTag(self, sender_id)

    def initialize_target_mode(self):
        """Configures the PN532 as target."""

    def poll_b(self):
        """Attempts to detect target for NFC type B."""
        raise RuntimeError("not implemented")

    def send_broadcast(self, broadcast):
        """Emits broadcast frame with CRC. This should be called after poll_a()."""
        raise RuntimeError("not implemented")

    def read_registers(self, *registers, cache=False):
        """
        Reads CIU registers
         :param registers: an iterable containing addresses of registers to read
         :param cache: prevents redundant register reads (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        raise RuntimeError("not implemented")

    def write_registers(self, registers: dict, cache=False) -> None:
        """
        (7.2.5) WriteRegister:
        Writes CIU registers
         :param registers: dictionary containing key-value pairs
         of register addresses and values to be written
         :param cache: prevents redundant register writes (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        raise RuntimeError("not implemented")

    def rf_configuration(self, cfg_item, value, *, cache=False):
        """
        (7.3.1) RFConfiguration
        Applies settings to one of the available configuration items
        :param cache: prevents redundant config writes (safe if not using IN_LIST_PASSIVE_TARGET)
        """
        raise RuntimeError("not implemented")

    def transceive_raw(
        self,
        data,
        type_="A",
        crc=True,
        bits=8,
        bitrate=106,
        *,
        power_level=100,
        timeout=1,
        cache_configuration=True
    ):
        """
        Configures the CIU with specified configuration and sends raw data
        :param timeout: Timeout in seconds
        :param cache_configuration: if true, prevents redundant register write and read commands
        """
        if power_level is not 100:
            self.send_command("SetPowerLevel", {"power_level": power_level/10})
        return self.transceive(data)

    def transceive(self, apdu):
        ret = self.transceive_multiple(None, [apdu])
        if ret is list:
            return ret[0]
        return None

    def ensure_connected(self):
        if self.conn is None:
            self.conn = HTTPSConnection(self.host, 1443, context=ssl._create_unverified_context())
            self.send_command("Init", {})
            self.rf_on = False


    def send_command(self, command, data):
        json_data = json.dumps(data)
        path = '/devices/' + self.id + '/services/CasimirControlService/' + command
        headers = {'Content-type': 'application/json'}
        self.ensure_connected()
        self.conn.request("POST", path, json_data, headers)
        response = self.conn.getresponse()
        rsp_json = response.read()
        self.log.debug("rsp_json: " + str(rsp_json))
        if str(rsp_json).startswith("b'rpc error"):
            return None
        rsp_str = json.loads(rsp_json)
        return json.loads(rsp_str)

    def transceive_multiple(self, senderId, command_apdus):
        self.unmute()
        command_apdus_hex = []
        for c in command_apdus:
            command_apdus_hex.append(c.hex())
        data = {}
        data["apdu_hex_strings"] = command_apdus_hex
        if senderId is dict and senderId["sender_id"] is int:
            data["sender_id"] = senderId["sender_id"]
        obj = self.send_command('SendApdu', data)
        if obj is None:
            return []
        resp_apdu_str = obj["responseHexStrings"]
        response_apdus_hex = []
        for r in resp_apdu_str:
            response_apdus_hex.append(bytearray.fromhex(r))
        return response_apdus_hex

    def unmute(self):
        """Turns on device's RF antenna."""
        self.ensure_connected()
        if not self.rf_on:
            self.rf_on = True
            self.send_command('SetRadioState', {"radio_on": True})

    def mute(self):
        """Turns off device's RF antenna."""
        if not self.conn is None:
            if self.rf_on:
                self.rf_on = False
                self.send_command('SetRadioState', {"radio_on": False})
            self.send_command("Close", {})
            self.conn.close()
            self.conn = None
        else:
            self.rf_on = False

    def execute_command(self, command, data=b'', timeout=0.5):
        raise RuntimeError("not implemented")

    def construct_frame(self, data):
        """Construct a data fram to be sent to the PN532."""
        raise RuntimeError("not implemented")

    def send_frame(self, frame, timeout=0.5):
        """
        Writes a frame to the device and returns the response.
        """
        raise RuntimeError("not implemented")

    def reset_buffers(self):
        """
        No buffers to reset
        """

    def get_device_response(self, timeout=0.5):
        """
        Confirms we get an ACK frame from device, reads response frame, and writes ACK.
        """
        raise RuntimeError("not implemented")

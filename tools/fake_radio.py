#!/usr/bin/env python3
"""A fake MeshCore companion radio on TCP, for driving the app without hardware.

    python3 tools/fake_radio.py [--port 5000]

Then in the app (emulator): Settings → Transports → enable TCP, and connect
to 10.0.2.2:5000 (the host as the emulator sees it).

This is a UI test fixture, not a protocol reference. Frame layouts mirror
what shared/.../protocol/ResponseParser.kt reads, which in turn follows the
firmware's companion_radio/MyMesh.cpp; when they disagree, the firmware
wins and this file is wrong. Framing per TcpInterface.kt: client→radio
'<' + u16le length, radio→client '>' + u16le length.

It answers the handshake, lists demo contacts and channels, delivers a few
queued messages (including a shared contact, channel and place, so the
message cards show), emits flooded RX-log packets from two "repeaters" so
the signal meter has something to show, and answers traces.
"""

import argparse
import hashlib
import random
import socket
import struct
import threading
import time

# Codes (shared/.../protocol/Codes.kt)
CMD_APP_START, CMD_SEND_TXT_MSG, CMD_SEND_CHANNEL_TXT_MSG, CMD_GET_CONTACTS = 1, 2, 3, 4
CMD_GET_DEVICE_TIME, CMD_SYNC_NEXT_MESSAGE, CMD_GET_BATT, CMD_DEVICE_QUERY = 5, 10, 20, 22
CMD_GET_CHANNEL, CMD_SEND_TRACE_PATH, CMD_GET_CUSTOM_VAR = 31, 36, 40
RESP_OK, RESP_ERR, RESP_CONTACTS_START, RESP_CONTACT, RESP_END_CONTACTS = 0, 1, 2, 3, 4
RESP_SELF_INFO, RESP_SENT, RESP_CURR_TIME, RESP_NO_MORE = 5, 6, 9, 10
RESP_BATT, RESP_DEVICE_INFO, RESP_CONTACT_MSG_V3, RESP_CHANNEL_MSG_V3 = 12, 13, 16, 17
RESP_CHANNEL_INFO, RESP_CUSTOM_VARS = 18, 21
PUSH_SEND_CONFIRMED, PUSH_MSG_WAITING, PUSH_LOG_RX, PUSH_TRACE = 0x82, 0x83, 0x88, 0x89

ADV_CHAT, ADV_REPEATER, ADV_ROOM, ADV_SENSOR = 1, 2, 3, 4
HASH_WIDTH = 2  # DEVICE_INFO byte 81 = width - 1


def key(seed: str) -> bytes:
    return hashlib.sha256(seed.encode()).digest()


SELF_KEY = key("meshconnect-dev-self")
SELF_NAME = "MeshConnect Dev"
SELF_POS = (39.4699, -0.3763)  # Valencia

# (name, type, lat, lon)
NODES = [
    ("Marta", ADV_CHAT, 39.4752, -0.3790),
    ("Pau EA5ABC", ADV_CHAT, None, None),
    ("Hilltop Rpt", ADV_REPEATER, 39.5098, -0.4205),
    ("Port Rpt", ADV_REPEATER, 39.4510, -0.3220),
    ("Albufera Rpt", ADV_REPEATER, 39.3330, -0.3520),
    ("Club room", ADV_ROOM, 39.4801, -0.3600),
    ("Weather Sensor", ADV_SENSOR, 39.4600, -0.4000),
]

PUBLIC_PSK = bytes.fromhex("8b3387e9c5cdea6ac9e5edbaa115cd72")
CHANNELS = [
    (0, "Public", PUBLIC_PSK),
    (1, "#valencia", key("#valencia")[:16]),
    # Long on purpose: MeshCore Hardened issue #8 (a long name showed as "#").
    (2, "#comunitat-valenciana-mesh", key("#comunitat-valenciana-mesh")[:16]),
]
MAX_CHANNELS = 8


def cstr(s: str, n: int) -> bytes:
    b = s.encode()[: n - 1]
    return b + b"\0" * (n - len(b))


def contact_frame(name, typ, lat, lon) -> bytes:
    k = key(name)
    path_len = 0xFF if typ == ADV_CHAT else ((HASH_WIDTH - 1) << 6) | 1
    path = (key("Hilltop Rpt")[:HASH_WIDTH] if path_len != 0xFF else b"").ljust(64, b"\0")
    now = int(time.time())
    return (
        bytes([RESP_CONTACT]) + k + bytes([typ, 0, path_len]) + path + cstr(name, 32)
        + struct.pack("<IiiI", now - random.randint(60, 7200),
                      int((lat or 0) * 1e6), int((lon or 0) * 1e6), now)
    )


def self_info() -> bytes:
    return (
        bytes([RESP_SELF_INFO, ADV_CHAT, 22, 22]) + SELF_KEY
        + struct.pack("<ii", int(SELF_POS[0] * 1e6), int(SELF_POS[1] * 1e6))
        + bytes([0, 0, 0, 0])  # multi-acks, advert loc policy, telemetry, manual-add
        + struct.pack("<II", 869525, 250000) + bytes([11, 5]) + SELF_NAME.encode() + b"\0"
    )


def device_info() -> bytes:
    return (
        bytes([RESP_DEVICE_INFO, 9, 100, MAX_CHANNELS]) + struct.pack("<I", 123456)
        + cstr("01 Nov 2026", 12) + cstr("Fake Radio (dev)", 40) + cstr("v1.16.0", 20)
        + bytes([0, HASH_WIDTH - 1])
    )


def channel_msg(idx, sender, text, hops=2) -> bytes:
    return (
        bytes([RESP_CHANNEL_MSG_V3, (random.randint(-40, 40)) & 0xFF, 0, 0, idx,
               ((HASH_WIDTH - 1) << 6) | hops, 0])
        + struct.pack("<I", int(time.time())) + f"{sender}: {text}".encode() + b"\0"
    )


def contact_msg(sender, text) -> bytes:
    return (
        bytes([RESP_CONTACT_MSG_V3, 24, 0, 0]) + key(sender)[:6] + bytes([1, 0])
        + struct.pack("<I", int(time.time())) + text.encode() + b"\0"
    )


def rx_log(repeater: str, snr_db: float, rssi: int) -> bytes:
    """A flooded GRP_TXT packet whose last relay is [repeater]."""
    hops = random.randint(1, 3)
    relays = [key(random.choice(["Albufera Rpt", "Port Rpt", "Hilltop Rpt"]))[:HASH_WIDTH] for _ in range(hops - 1)]
    relays.append(key(repeater)[:HASH_WIDTH])
    header = (5 << 2) | 1  # payload GRP_TXT, route FLOOD (Packet.h:15)
    pkt = bytes([header, ((HASH_WIDTH - 1) << 6) | hops]) + b"".join(relays) + random.randbytes(24)
    return bytes([PUSH_LOG_RX, int(snr_db * 4) & 0xFF, rssi & 0xFF]) + pkt


class Session:
    def __init__(self, conn):
        self.conn = conn
        self.lock = threading.Lock()
        self.alive = True
        self.queue = []

    def send(self, payload: bytes):
        with self.lock:
            self.conn.sendall(b">" + struct.pack("<H", len(payload)) + payload)

    def seed_messages(self):
        mk = key("Marta").hex()
        self.queue = [
            channel_msg(0, "Pau EA5ABC", "Morning all, anyone on the Hilltop repeater?"),
            channel_msg(0, "Marta", "Loud and clear from the city centre"),
            channel_msg(0, "Marta", f"<{key('Hilltop Rpt').hex()}:2:Hilltop Rpt>"),
            channel_msg(0, "Pau EA5ABC", "meshcore://channel/add?name=%23valencia&secret=" + CHANNELS[1][2].hex()),
            channel_msg(1, "Marta", "39.47520,-0.37900"),
            channel_msg(1, "Pau EA5ABC", "meshcore://channel/add?name=%23fallas&secret=" + key("#fallas")[:16].hex()),
            contact_msg("Marta", "Hi! Are you coming to the meetup?"),
            contact_msg("Marta", f"<{key('Pau EA5ABC').hex()}:1:Pau EA5ABC>"),
            contact_msg("Marta", "39.46990,-0.37630"),
        ]
        self.send(bytes([PUSH_MSG_WAITING]))

    def handle(self, frame: bytes):
        cmd = frame[0]
        if cmd == CMD_APP_START:
            self.send(self_info())
            threading.Timer(4.0, self.seed_messages).start()
        elif cmd == CMD_DEVICE_QUERY:
            self.send(device_info())
        elif cmd == CMD_GET_DEVICE_TIME:
            self.send(bytes([RESP_CURR_TIME]) + struct.pack("<I", int(time.time())))
        elif cmd == CMD_GET_CONTACTS:
            self.send(bytes([RESP_CONTACTS_START]) + struct.pack("<I", len(NODES)))
            for n in NODES:
                self.send(contact_frame(*n))
            self.send(bytes([RESP_END_CONTACTS]) + struct.pack("<I", int(time.time())))
        elif cmd == CMD_GET_CHANNEL:
            idx = frame[1] if len(frame) > 1 else 0
            ch = next((c for c in CHANNELS if c[0] == idx), (idx, "", bytes(16)))
            self.send(bytes([RESP_CHANNEL_INFO, idx]) + cstr(ch[1], 32) + ch[2])
        elif cmd == CMD_SYNC_NEXT_MESSAGE:
            self.send(self.queue.pop(0) if self.queue else bytes([RESP_NO_MORE]))
        elif cmd == CMD_GET_BATT:
            self.send(bytes([RESP_BATT]) + struct.pack("<HII", 3870, 512, 4096))
        elif cmd == CMD_GET_CUSTOM_VAR:
            self.send(bytes([RESP_CUSTOM_VARS]) + b"gps:0\0")
        elif cmd in (CMD_SEND_TXT_MSG, CMD_SEND_CHANNEL_TXT_MSG):
            ack = random.getrandbits(32)
            self.send(bytes([RESP_SENT, 1]) + struct.pack("<II", ack, 8000))
            if cmd == CMD_SEND_TXT_MSG:
                threading.Timer(2.5, lambda: self.send(bytes([PUSH_SEND_CONFIRMED]) + struct.pack("<II", ack, 2400))).start()
        elif cmd == 56 and len(frame) > 1 and frame[1] == 1:  # CMD_GET_STATS, STATS_TYPE_RADIO
            self.send(bytes([24, 1]) + struct.pack("<hbbII", random.randint(-118, -108),
                                                    random.randint(-110, -70), random.randint(-40, 40), 1234, 5678))
        elif cmd == CMD_SEND_TRACE_PATH:
            self.trace(frame)
        elif cmd == 26:  # CMD_SEND_LOGIN: [26][pubkey 32][password]
            # SENT, then PUSH_CODE_LOGIN_SUCCESS [0x85][perm][prefix 6][ts u32][acl]:
            # admin for any password, so the hub can be driven.
            self.send(bytes([RESP_SENT, 0]) + struct.pack("<II", 0, 4000))
            prefix = frame[1:7]
            threading.Timer(0.8, lambda: self.send(bytes([0x85, 1]) + prefix + struct.pack("<I", int(time.time())) + bytes([3]))).start()
        else:
            self.send(bytes([RESP_OK]))

    def trace(self, frame: bytes):
        """[36][tag u32][auth u32][flags][path…] → SENT, then TRACE_DATA laid out as
        companion_radio/MyMesh.cpp:855-876 writes it (hashes, one SNR per hop, final SNR)."""
        tag = frame[1:5]
        flags = frame[9] if len(frame) > 9 else 0
        path = frame[10:]
        self.send(bytes([RESP_SENT, 0]) + tag + struct.pack("<I", 6000))
        width = 1 << (flags & 0x03)
        hops = len(path) // max(width, 1)

        def reply():
            snrs = bytes(random.randint(-30, 40) & 0xFF for _ in range(hops))
            last = random.randint(-20, 40) & 0xFF
            self.send(bytes([PUSH_TRACE, 0, len(path), flags]) + tag + frame[5:9] + path + snrs + bytes([last]))
        threading.Timer(1.5 + 0.4 * hops, reply).start()

    def noise(self):
        """Packets heard from two repeaters, so the meter has a reading."""
        while self.alive:
            time.sleep(random.uniform(3, 8))
            try:
                rep = random.choice([("Hilltop Rpt", 6.5, -78), ("Port Rpt", -4.25, -105)])
                self.send(rx_log(rep[0], rep[1] + random.uniform(-1.5, 1.5), rep[2]))
            except OSError:
                return

    def run(self):
        threading.Thread(target=self.noise, daemon=True).start()
        buf = b""
        while True:
            data = self.conn.recv(4096)
            if not data:
                break
            buf += data
            while len(buf) >= 3:
                if buf[0] != 0x3C:
                    buf = buf[1:]
                    continue
                n = struct.unpack("<H", buf[1:3])[0]
                if len(buf) < 3 + n:
                    break
                frame, buf = buf[3:3 + n], buf[3 + n:]
                print(f"<- cmd {frame[0]} ({n} bytes)")
                self.handle(frame)
        self.alive = False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5000)
    args = ap.parse_args()
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", args.port))
    srv.listen(1)
    print(f"fake radio on :{args.port}")
    while True:
        conn, addr = srv.accept()
        print("connected", addr)
        try:
            Session(conn).run()
        except OSError as e:
            print("session ended:", e)
        print("disconnected")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generate test audio (sine wave WAV) for emulator smoke tests. Zero dependencies."""
import math
import os
import struct
import sys
import wave


def main(path, freq=440.0, seconds=30):
    rate = 44100
    n = int(rate * seconds)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        # 440Hz with a gentle fade in/out so it clicks less
        frames = bytearray()
        for i in range(n):
            t = i / rate
            fade = min(1.0, i / rate * 4.0, (seconds - t) * 4.0)
            v = int(32000 * fade * math.sin(2 * math.pi * freq * t))
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))
    print("wrote", path, os.path.getsize(path), "bytes")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "test_tone.wav",
         float(sys.argv[2]) if len(sys.argv) > 2 else 440.0,
         float(sys.argv[3]) if len(sys.argv) > 3 else 30.0)

#!/usr/bin/env python3
"""生成三首可辨认的示范旋律 WAV（小星星/两只老虎/欢乐颂），用于模拟器试用。"""
import math
import struct
import sys
import wave

RATE = 44100
NOTE = 0.42  # 每个音的秒数
VOLUME = 0.45

# 简谱音名 -> 频率（C 大调，C4=261.63）
FREQ = {
    "C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23,
    "G4": 392.00, "A4": 440.00, "B4": 493.88, "C5": 523.25,
    "G3": 196.00, "A3": 220.00, "E5": 659.25, "D5": 587.33,
}

SONGS = {
    "小星星": "C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4 "
             "G4 G4 F4 F4 E4 E4 D4 G4 G4 F4 F4 E4 E4 D4 "
             "C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4",
    "两只老虎": "C4 D4 E4 C4 C4 D4 E4 C4 E4 F4 G4 E4 F4 G4 "
              "G4 A4 G4 F4 E4 C4 G4 A4 G4 F4 E4 C4 C4 G3 C4 C4 G3 C4",
    "欢乐颂": "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4 D4 D4 "
             "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4 C4 C4",
}


def write_song(path, notes):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = bytearray()
        for name in notes.split():
            f = FREQ[name]
            n = int(RATE * NOTE)
            for i in range(n):
                t = i / RATE
                # 音头音尾各留 20ms 淡入淡出，避免咔哒声
                fade = min(1.0, t / 0.02, (NOTE - t) / 0.02)
                # 加一点二次谐波，音色更像“叮”而不是电话音
                v = VOLUME * fade * (
                    math.sin(2 * math.pi * f * t)
                    + 0.25 * math.sin(2 * math.pi * 2 * f * t)
                )
                frames += struct.pack("<h", int(32767 * max(-1, min(1, v))))
        w.writeframes(bytes(frames))
    print("wrote", path)


if __name__ == "__main__":
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/demo_songs"
    import os
    os.makedirs(out_dir, exist_ok=True)
    for title, notes in SONGS.items():
        write_song(f"{out_dir}/{title}.wav", notes)

# -*- coding: utf-8 -*-
"""用 pygame 解码 MP3，转成 Java javax.sound.sampled 可用的 16bit PCM WAV"""
import pygame
import wave
import os
import sys

def convert(src, dst):
    freq, fmt, chans = pygame.mixer.get_init()
    sound = pygame.mixer.Sound(src)
    raw = sound.get_raw()
    # fmt: -16 = signed 16-bit little endian
    sampwidth = abs(fmt) // 8
    with wave.open(dst, "wb") as w:
        w.setnchannels(chans)
        w.setsampwidth(sampwidth)
        w.setframerate(freq)
        w.writeframes(raw)
    print("OK: %s -> %s (%d bytes, %dch %dbit %dHz)" % (src, dst, len(raw), chans, sampwidth*8, freq))

def main():
    pygame.mixer.init(44100, -16, 2)
    pairs = [
        ("music/background-music.mp3", "music/background-music.wav"),
        ("music/eat card.mp3",         "music/eat card.wav"),
        ("music/fai.mp3",              "music/fai.wav"),
        ("music/sucess.mp3",           "music/sucess.wav"),
    ]
    for src, dst in pairs:
        if not os.path.exists(src):
            print("MISS: " + src)
            continue
        try:
            convert(src, dst)
        except Exception as e:
            print("FAIL: %s -> %s : %s" % (src, dst, e))
    pygame.mixer.quit()

if __name__ == "__main__":
    main()
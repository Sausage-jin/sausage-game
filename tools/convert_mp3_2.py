# -*- coding: utf-8 -*-
"""转换游戏背景音乐mp3为wav"""
import pygame
import wave
import os

def convert(src, dst):
    freq, fmt, chans = pygame.mixer.get_init()
    sound = pygame.mixer.Sound(src)
    raw = sound.get_raw()
    sampwidth = abs(fmt) // 8
    with wave.open(dst, "wb") as w:
        w.setnchannels(chans)
        w.setsampwidth(sampwidth)
        w.setframerate(freq)
        w.writeframes(raw)
    print("OK: %s -> %s (%d bytes)" % (src, dst, len(raw)))

def main():
    pygame.mixer.init(44100, -16, 2)
    pairs = [
        ("music/lantoubeiqiang-background.mp3", "music/lantoubeiqiang-background.wav"),
        ("music/wuziqibackgroung.mp3",          "music/wuziqibackgroung.wav"),
        ("music/eluosibackground.mp3",          "music/eluosibackground.wav"),
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
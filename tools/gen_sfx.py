# -*- coding: utf-8 -*-
"""
为游戏生成全套英文命名音效（WAV, 22050Hz, 16bit, 单声道 PCM）。
纯标准库实现（wave/math/random/struct），无需第三方依赖。
输出目录: music/
"""
import wave
import math
import random
import struct
import os

SR = 22050           # 采样率
OUT = "music"
random.seed(20261116)

if not os.path.isdir(OUT):
    os.makedirs(OUT)


# ---------------- 基础合成工具 ----------------

def _clip(x):
    if x > 1.0:
        return 1.0
    if x < -1.0:
        return -1.0
    return x


def envelope(i, n, attack=0.01, decay=0.3, sustain=0.6, release=0.2):
    """简化 ADSR 包络，返回 0..1 增益。attack/decay/sustain/release 为比例。"""
    t = i / float(n)
    a = max(attack, 1e-4)
    d = max(decay, 1e-4)
    r = max(release, 1e-4)
    if t < a:
        return t / a
    if t < a + d:
        return 1.0 - (1.0 - sustain) * ((t - a) / d)
    if t < 1.0 - r:
        return sustain
    return sustain * (1.0 - (t - (1.0 - r)) / r)


def osc(shape, phase):
    """根据波形返回 -1..1 采样。phase 单位: 弧度。"""
    if shape == "sine":
        return math.sin(phase)
    if shape == "square":
        return 1.0 if math.sin(phase) >= 0 else -1.0
    if shape == "tri":
        return 2.0 / math.pi * math.asin(math.sin(phase))
    if shape == "saw":
        return 2.0 * ((phase / (2 * math.pi)) % 1.0) - 1.0
    return math.sin(phase)


def tone(freq, dur, shape="sine", vol=0.5, attack=0.01, decay=0.3,
         sustain=0.6, release=0.25, bend=0.0, vib=0.0, vib_rate=6.0):
    """生成单音。bend: 指数扫频（结束频率倍数）。vib: 颤音深度(0..1)。"""
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / float(n)
        f = freq * (bend ** t) if bend != 0.0 else freq
        if vib > 0.0:
            f *= 1.0 + vib * math.sin(2 * math.pi * vib_rate * (i / float(SR)))
        phase += 2 * math.pi * f / SR
        g = envelope(i, n, attack, decay, sustain, release)
        out.append(osc(shape, phase) * vol * g)
    return out


def sweep(f1, f2, dur, shape="sine", vol=0.5, attack=0.01, release=0.2):
    """线性扫频 f1->f2。"""
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / float(n)
        f = f1 + (f2 - f1) * t
        phase += 2 * math.pi * f / SR
        g = envelope(i, n, attack, 0.05, 0.9, release)
        out.append(osc(shape, phase) * vol * g)
    return out


def noise(dur, vol=0.4, attack=0.005, decay=0.2, sustain=0.4, release=0.2,
          lp=1.0):
    """白噪声，lp 为低通平滑系数(0..1, 越小越闷)。"""
    n = int(SR * dur)
    out = []
    prev = 0.0
    for i in range(n):
        w = random.uniform(-1.0, 1.0)
        prev = prev + lp * (w - prev)
        g = envelope(i, n, attack, decay, sustain, release)
        out.append(prev * vol * g)
    return out


def hit(freq, dur, vol=0.6, shape="sine", body=0.4):
    """敲击感：快速起振的基频 + 少量噪声 body。"""
    n = int(SR * dur)
    out = []
    phase = 0.0
    prev = 0.0
    for i in range(n):
        phase += 2 * math.pi * freq / SR
        w = random.uniform(-1.0, 1.0)
        prev = prev + 0.5 * (w - prev)
        g = envelope(i, n, 0.002, 0.12, 0.25, 0.5)
        out.append((osc(shape, phase) + prev * body) * vol * g)
    return out


def mix(*tracks):
    """多轨混合（长度取最长）。"""
    m = max(len(t) for t in tracks)
    out = [0.0] * m
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def swish(dur, vol=0.45, lp1=0.15, lp2=0.9, attack=0.15, release=0.35):
    """纸面摩擦声：低通系数随时间变化的白噪声，模拟"唰"的由闷到亮。"""
    n = int(SR * dur)
    out = []
    prev = 0.0
    for i in range(n):
        t = i / float(n)
        lp = lp1 + (lp2 - lp1) * t
        w = random.uniform(-1.0, 1.0)
        prev = prev + lp * (w - prev)
        g = envelope(i, n, attack, 0.1, 0.85, release)
        out.append(prev * vol * g)
    return out


def seq(notes, gap=0.0):
    """把多个音轨按顺序拼接，gap 为间隔秒。"""
    out = []
    g = int(SR * gap)
    for t in notes:
        out.extend(t)
        out.extend([0.0] * g)
    return out


def chord(freqs, dur, shape="sine", vol=0.4, **kw):
    """和弦：多频率叠加。"""
    parts = [tone(f, dur, shape, vol / max(1, len(freqs)), **kw) for f in freqs]
    return mix(*parts)


def norm(samples, peak=0.95):
    m = max((abs(s) for s in samples), default=0.0)
    if m < 1e-6:
        return samples
    k = peak / m
    return [s * k for s in samples]


COUNT = [0]


def save(name, samples):
    samples = norm(samples)
    path = os.path.join(OUT, name + ".wav")
    w = wave.open(path, "wb")
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    frames = b"".join(struct.pack("<h", int(_clip(s) * 32767)) for s in samples)
    w.writeframes(frames)
    w.close()
    COUNT[0] += 1
    print("  %-22s %6.2f KB" % (name + ".wav", len(frames) / 1024.0))


# ---------------- 各音效定义 ----------------

def build():
    print("生成音效 -> %s/" % OUT)

    # ===== 通用 UI =====
    save("ui-click",      mix(noise(0.02, 0.30, attack=0.001, decay=0.25, sustain=0.15, release=0.4, lp=0.55),
                              hit(720, 0.022, 0.30, "sine", 0.2)))
    save("ui-confirm",    seq([tone(660, 0.07, "tri", 0.5), tone(990, 0.12, "tri", 0.5)], 0.02))
    save("ui-cancel",     seq([tone(620, 0.07, "tri", 0.45), tone(415, 0.13, "tri", 0.45)], 0.02))
    save("ui-select",     tone(760, 0.05, "sine", 0.45, release=0.3))

    # ===== 通用游戏 =====
    save("game-start",    seq([tone(523, 0.09, "tri", 0.5), tone(659, 0.09, "tri", 0.5),
                               tone(784, 0.16, "tri", 0.55)], 0.02))
    save("game-win",      seq([tone(523, 0.12, "tri", 0.55), tone(659, 0.12, "tri", 0.55),
                               tone(784, 0.12, "tri", 0.55), tone(1047, 0.32, "tri", 0.6)], 0.03))
    save("game-lose",     seq([tone(440, 0.14, "saw", 0.4), tone(349, 0.14, "saw", 0.4),
                               tone(262, 0.30, "saw", 0.4)], 0.03))
    save("game-draw",     seq([tone(494, 0.14, "tri", 0.5), tone(494, 0.24, "tri", 0.5)], 0.04))
    save("turn-start",    tone(988, 0.10, "sine", 0.5, release=0.35))

    # ===== 小鸟飞翔 =====
    save("bird-flap",     noise(0.09, 0.35, attack=0.003, decay=0.1, sustain=0.3, release=0.3, lp=0.6))
    save("bird-hit",      mix(hit(160, 0.22, 0.7, "sine", 0.6), noise(0.18, 0.4, lp=0.4)))
    save("coin-pickup",   seq([tone(1319, 0.06, "sine", 0.5), tone(1760, 0.14, "sine", 0.55)], 0.01))
    save("score-point",   tone(1175, 0.08, "square", 0.35, release=0.3))

    # ===== 烂头背枪 =====
    save("card-flip",     swish(0.10, 0.42, lp1=0.12, lp2=0.85, attack=0.2, release=0.3))
    save("card-move",     noise(0.10, 0.25, attack=0.004, decay=0.12, sustain=0.3, release=0.3, lp=0.3))
    save("card-eat",      mix(hit(300, 0.14, 0.6, "square", 0.5), noise(0.12, 0.35, lp=0.5)))
    save("card-merge",    seq([tone(523, 0.08, "tri", 0.5), tone(784, 0.16, "tri", 0.55)], 0.02))
    save("card-select",   hit(700, 0.05, 0.4, "sine", 0.1))
    save("card-deal",     seq([noise(0.05, 0.3, lp=0.5), noise(0.05, 0.3, lp=0.5),
                               noise(0.05, 0.3, lp=0.5)], 0.03))
    save("battle-attack", mix(sweep(700, 180, 0.18, "saw", 0.5), noise(0.15, 0.35, lp=0.6)))

    # ===== 五子棋 =====
    save("chess-place",   hit(950, 0.07, 0.6, "sine", 0.25))

    # ===== 俄罗斯方块 =====
    save("tetris-move",   tone(330, 0.04, "square", 0.3, release=0.2))
    save("tetris-rotate", tone(494, 0.05, "square", 0.32, release=0.2))
    save("tetris-drop",   sweep(600, 200, 0.07, "square", 0.3))
    save("tetris-lock",   hit(220, 0.09, 0.6, "sine", 0.4))
    save("tetris-clear",  seq([tone(659, 0.07, "square", 0.45), tone(880, 0.07, "square", 0.45),
                               tone(1109, 0.14, "square", 0.5)], 0.02))

    # ===== 你画我猜 =====
    save("draw-color",    tone(1047, 0.06, "sine", 0.45, release=0.3))
    save("draw-erase",    noise(0.12, 0.28, attack=0.005, decay=0.15, sustain=0.4, release=0.3, lp=0.35))
    save("draw-clear",    sweep(900, 200, 0.20, "sine", 0.4))
    save("guess-correct", seq([tone(784, 0.09, "tri", 0.55), tone(988, 0.09, "tri", 0.55),
                               tone(1319, 0.22, "tri", 0.6)], 0.02))
    save("guess-wrong",   seq([tone(392, 0.12, "saw", 0.4), tone(294, 0.22, "saw", 0.4)], 0.03))
    save("tick-tock",     hit(1200, 0.04, 0.5, "square", 0.1))

    # ===== 聊天 / 联网 =====
    save("chat-send",     sweep(700, 1100, 0.08, "sine", 0.4))
    save("chat-receive",  seq([tone(880, 0.07, "sine", 0.5), tone(1175, 0.12, "sine", 0.5)], 0.02))
    save("net-connect",   seq([tone(659, 0.08, "tri", 0.5), tone(880, 0.08, "tri", 0.5),
                               tone(1319, 0.16, "tri", 0.55)], 0.02))
    save("net-disconnect",seq([tone(784, 0.10, "tri", 0.45), tone(523, 0.18, "tri", 0.45)], 0.03))
    save("notify",        seq([tone(1175, 0.08, "sine", 0.5), tone(1175, 0.14, "sine", 0.5)], 0.05))

    print("完成，共生成 %d 个音效。" % COUNT[0])


if __name__ == "__main__":
    build()

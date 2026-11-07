#!/usr/bin/env python3
"""
Generates every audio asset used by Kappu Suppu from scratch (pure synthesis: sine waves,
filtered noise, envelopes). Nothing is sampled or downloaded, so the assets are original works
released under CC0 1.0 (public domain dedication) by the project.

Usage:  python3 tools/generate_audio.py        (needs numpy, scipy and ffmpeg with libvorbis)
Output: app/src/main/res/raw/*.ogg
"""
import os
import subprocess
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 22050
RNG = np.random.default_rng(20261107)
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def band(x, lo, hi, order=4):
    sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def lowpass(x, hi, order=4):
    sos = butter(order, hi, btype="low", fs=SR, output="sos")
    return sosfilt(sos, x)


def highpass(x, lo, order=4):
    sos = butter(order, lo, btype="high", fs=SR, output="sos")
    return sosfilt(sos, x)


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def normalize(x, peak):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, fin=0.01, fout=0.05):
    x = x.copy()
    n_in, n_out = int(SR * fin), int(SR * fout)
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def seamless(noise, loop_len, xfade):
    """Make a noise bed loop without a click by cross-fading its tail into its head."""
    n, f = int(SR * loop_len), int(SR * xfade)
    out = noise[:n].copy()
    w = np.linspace(0, 1, f)
    out[:f] = out[:f] * w + noise[n:n + f] * (1 - w)
    return out


def page_flip():
    sec = 0.95
    t = t_axis(sec)
    noise = RNG.standard_normal(len(t))
    # soft whoosh of a page being lifted and dropped
    whoosh = band(noise, 700, 5200)
    env = np.exp(-((t - 0.28) / 0.17) ** 2) * 0.9 + np.exp(-((t - 0.62) / 0.12) ** 2) * 0.55
    sig = whoosh * env
    # crisp paper flutter clicks
    for c in (0.05, 0.11, 0.17, 0.52, 0.58, 0.66):
        i = int(c * SR)
        n = int(0.012 * SR)
        click = highpass(RNG.standard_normal(n), 2500) * np.hanning(n)
        sig[i:i + n] += click * 1.4
    return fade(normalize(sig, 0.8), 0.005, 0.12)


def pencil_scratch():
    loop = 1.4
    t = t_axis(loop + 0.1)
    noise = RNG.standard_normal(len(t))
    grit = band(noise, 1800, 7000)
    # scrubbing: a few back-and-forth strokes, each with its own pressure
    mod = np.zeros_like(t)
    stroke_len = 0.175
    for k in range(int(loop / stroke_len)):
        a = k * stroke_len
        seg = (t >= a) & (t < a + stroke_len)
        phase = (t[seg] - a) / stroke_len
        mod[seg] = np.sin(np.pi * phase) ** 0.7 * (0.55 + 0.45 * RNG.random())
    mod = lowpass(mod, 40, 2) * 0.7 + 0.12
    sig = grit * mod + highpass(noise, 4500) * 0.12 * mod
    sig = seamless(sig, loop, 0.1)
    return normalize(sig, 0.55)


def suspense_loop():
    loop = 24.0
    t = t_axis(loop)
    sig = np.zeros_like(t)
    # dark drone: every frequency makes a whole number of cycles in 24 s -> seamless loop
    for f, a in ((55.0, 0.55), (82.5, 0.28), (58.25, 0.22), (110.0, 0.10), (116.5, 0.07)):
        sig += a * np.sin(2 * np.pi * f * t)
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * (3 / loop) * t)
    sig *= trem
    # slow breathing noise bed
    bed = seamless(lowpass(RNG.standard_normal(len(t) + int(1.5 * SR)), 380), loop, 1.5)
    sig += bed * 0.5 * (0.6 + 0.4 * np.sin(2 * np.pi * (2 / loop) * t + 1.0))
    # soft heartbeat every 1.5 s (16 per loop)
    beat = np.zeros_like(t)
    for k in range(16):
        for off, amp in ((0.0, 1.0), (0.26, 0.6)):
            i = int((k * 1.5 + off) * SR)
            n = int(0.22 * SR)
            if i + n >= len(beat):
                continue
            tt = np.arange(n) / SR
            beat[i:i + n] += amp * np.sin(2 * np.pi * 48 * tt) * np.exp(-tt * 18)
    sig += beat * 0.55
    # sparse, quiet high "creaks" at fixed times
    for sec, f in ((5.0, 1244.5), (11.5, 1396.9), (17.0, 1174.7), (21.5, 1318.5)):
        i = int(sec * SR)
        n = int(1.6 * SR)
        tt = np.arange(n) / SR
        env = np.sin(np.pi * tt / 1.6) ** 3
        sig[i:i + n] += 0.05 * np.sin(2 * np.pi * f * tt + 2 * np.sin(2 * np.pi * 5.5 * tt)) * env
    return normalize(sig, 0.5)


def tension_cue():
    sec = 3.0
    t = t_axis(sec)
    # rising string-like swell with vibrato, stacked in dissonant intervals, then a short sting
    glide = 330 + 330 * (t / sec) ** 1.6
    vib = 1 + 0.012 * np.sin(2 * np.pi * 6.2 * t)
    sig = np.zeros_like(t)
    for ratio, amp in ((1.0, 0.5), (1.0595, 0.35), (1.5, 0.3), (1.414, 0.25)):
        phase = 2 * np.pi * np.cumsum(glide * ratio * vib) / SR
        voice = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase)
        sig += amp * voice
    swell = (t / sec) ** 1.8
    sig *= swell
    sig *= 0.8 + 0.2 * np.sin(2 * np.pi * 9 * t)  # tremolo
    # low pulse that speeds up
    pulse_t = 0.0
    gap = 0.55
    while pulse_t < sec - 0.3:
        i = int(pulse_t * SR)
        n = int(0.25 * SR)
        tt = np.arange(n) / SR
        sig[i:i + n] += 0.6 * swell[i] * np.sin(2 * np.pi * 52 * tt) * np.exp(-tt * 14)
        pulse_t += gap
        gap = max(0.2, gap * 0.82)
    return fade(normalize(sig, 0.7), 0.02, 0.5)


def write_ogg(name, samples, quality="4"):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        wavfile.write(wav, SR, pcm)
        dest = os.path.join(OUT, name + ".ogg")
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", quality, dest],
            check=True,
        )
        print(f"wrote {dest} ({os.path.getsize(dest) // 1024} KB, {len(samples) / SR:.1f}s)")


if __name__ == "__main__":
    write_ogg("page_flip", page_flip())
    write_ogg("pencil_scratch", pencil_scratch())
    write_ogg("suspense_loop", suspense_loop(), "2")
    write_ogg("tension_cue", tension_cue())

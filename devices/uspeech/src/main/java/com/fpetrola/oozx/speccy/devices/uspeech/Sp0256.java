/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.fpetrola.oozx.speccy.devices.uspeech;

/**
 * General Instrument's SP0256 speech chip as Fuse has it, which took it from jzIntv: a
 * microsequencer that reads its instructions a few bits at a time out of its own ROM, and a
 * twelve-pole LPC filter those instructions load, making a sample every few hundred T-states.
 * <p>
 * The machine has two wires to it. The address load (ALD) is the allophone to say next, and the
 * load request (LRQ) is low while one is still waiting to be taken. Its time is the machine's: it
 * runs up to the T-state it is asked something at, and to the end of the frame when one ends.
 * <p>
 * It keeps to the letter of Fuse's code, the overflow of its sixteen- and eight-bit arithmetic
 * included: a test plays the same script through both and compares every sample.
 */
final class Sp0256 {

  /** Where the samples go: the level from that T-state of the frame on. */
  interface Output {
    void sample(long tstates, int level);
  }

  private static final int SCBUF_SIZE = 4096, SCBUF_MASK = SCBUF_SIZE - 1;
  private static final int PER_PAUSE = 64, PER_NOISE = 64;
  private static final int FIFO_ADDR = 0x1800 << 3;
  private static final int AM = 0, PR = 1, B0 = 2, F0 = 3, B1 = 4, F1 = 5, B2 = 6, F2 = 7, B3 = 8, F3 = 9,
      B4 = 10, F4 = 11, B5 = 12, F5 = 13, IA = 14, IP = 15;

  /** One step of a data block: how many bits, how far up, into which register, and how. */
  private static int cr(int length, int shift, int register, int delta, int field, int clear5, int clearAll) {
    return (length & 15) | (shift & 15) << 4 | (register & 15) << 8 | (delta & 1) << 12 | (field & 1) << 13
        | (clear5 & 1) << 14 | (clearAll & 1) << 15;
  }

  private static final int CR_DELTA = cr(0, 0, 0, 1, 0, 0, 0), CR_FIELD = cr(0, 0, 0, 0, 1, 0, 0),
      CR_CLRL = cr(0, 0, 0, 0, 0, 0, 1);

  private static final short[] QTBL = {0, 9, 17, 25, 33, 41, 49, 57, 65, 73, 81, 89, 97, 105, 113, 121, 129, 137, 145, 153, 161, 169, 177, 185, 193, 201, 209, 217, 225, 233, 241, 249, 257, 265, 273, 281, 289, 297, 301, 305, 309, 313, 317, 321, 325, 329, 333, 337, 341, 345, 349, 353, 357, 361, 365, 369, 373, 377, 381, 385, 389, 393, 397, 401, 405, 409, 413, 417, 421, 425, 427, 429, 431, 433, 435, 437, 439, 441, 443, 445, 447, 449, 451, 453, 455, 457, 459, 461, 463, 465, 467, 469, 471, 473, 475, 477, 479, 481, 482, 483, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 495, 496, 497, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511};
  private static final int[] DATAFMT = {
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(8, 0, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(8, 0, B0, 0, 0, 0, 0),
      cr(8, 0, F0, 0, 0, 0, 0),
      cr(8, 0, B1, 0, 0, 0, 0),
      cr(8, 0, F1, 0, 0, 0, 0),
      cr(8, 0, B2, 0, 0, 0, 0),
      cr(8, 0, F2, 0, 0, 0, 0),
      cr(8, 0, B3, 0, 0, 0, 0),
      cr(8, 0, F3, 0, 0, 0, 0),
      cr(8, 0, B4, 0, 0, 0, 0),
      cr(8, 0, F4, 0, 0, 0, 0),
      cr(8, 0, B5, 0, 0, 0, 0),
      cr(8, 0, F5, 0, 0, 0, 0),
      cr(8, 0, IA, 0, 0, 0, 0),
      cr(8, 0, IP, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 1),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(4, 3, B3, 0, 0, 0, 0),
      cr(6, 2, F3, 0, 0, 0, 0),
      cr(7, 1, B4, 0, 0, 0, 0),
      cr(6, 2, F4, 0, 0, 0, 0),
      cr(8, 0, B5, 0, 0, 0, 0),
      cr(8, 0, F5, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 1),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(6, 1, B3, 0, 0, 0, 0),
      cr(7, 1, F3, 0, 0, 0, 0),
      cr(8, 0, B4, 0, 0, 0, 0),
      cr(8, 0, F4, 0, 0, 0, 0),
      cr(8, 0, B5, 0, 0, 0, 0),
      cr(8, 0, F5, 0, 0, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(6, 2, F3, 0, 1, 0, 0),
      cr(6, 2, F4, 0, 1, 0, 0),
      cr(8, 0, F5, 0, 1, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(7, 1, F3, 0, 1, 0, 0),
      cr(8, 0, F4, 0, 1, 0, 0),
      cr(8, 0, F5, 0, 1, 0, 0),
      0,
      0,
      cr(4, 2, AM, 1, 0, 0, 0),
      cr(5, 0, PR, 1, 0, 0, 0),
      cr(3, 4, B0, 1, 0, 0, 0),
      cr(3, 3, F0, 1, 0, 0, 0),
      cr(3, 4, B1, 1, 0, 0, 0),
      cr(3, 3, F1, 1, 0, 0, 0),
      cr(3, 4, B2, 1, 0, 0, 0),
      cr(3, 3, F2, 1, 0, 0, 0),
      cr(3, 3, B3, 1, 0, 0, 0),
      cr(4, 2, F3, 1, 0, 0, 0),
      cr(4, 1, B4, 1, 0, 0, 0),
      cr(4, 2, F4, 1, 0, 0, 0),
      cr(5, 0, B5, 1, 0, 0, 0),
      cr(5, 0, F5, 1, 0, 0, 0),
      cr(4, 2, AM, 1, 0, 0, 0),
      cr(5, 0, PR, 1, 0, 0, 0),
      cr(4, 1, B0, 1, 0, 0, 0),
      cr(4, 2, F0, 1, 0, 0, 0),
      cr(4, 1, B1, 1, 0, 0, 0),
      cr(4, 2, F1, 1, 0, 0, 0),
      cr(4, 1, B2, 1, 0, 0, 0),
      cr(4, 2, F2, 1, 0, 0, 0),
      cr(4, 1, B3, 1, 0, 0, 0),
      cr(5, 1, F3, 1, 0, 0, 0),
      cr(5, 0, B4, 1, 0, 0, 0),
      cr(5, 0, F4, 1, 0, 0, 0),
      cr(5, 0, B5, 1, 0, 0, 0),
      cr(5, 0, F5, 1, 0, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(5, 3, F0, 0, 1, 0, 0),
      cr(5, 3, F1, 0, 1, 0, 0),
      cr(5, 3, F2, 0, 1, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(6, 2, F0, 0, 1, 0, 0),
      cr(6, 2, F1, 0, 1, 0, 0),
      cr(6, 2, F2, 0, 1, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(3, 4, B0, 0, 0, 0, 0),
      cr(5, 3, F0, 0, 0, 0, 0),
      cr(3, 4, B1, 0, 0, 0, 0),
      cr(5, 3, F1, 0, 0, 0, 0),
      cr(3, 4, B2, 0, 0, 0, 0),
      cr(5, 3, F2, 0, 0, 0, 0),
      cr(4, 3, B3, 0, 0, 0, 0),
      cr(6, 2, F3, 0, 0, 0, 0),
      cr(7, 1, B4, 0, 0, 0, 0),
      cr(6, 2, F4, 0, 0, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(6, 1, B0, 0, 0, 0, 0),
      cr(6, 2, F0, 0, 0, 0, 0),
      cr(6, 1, B1, 0, 0, 0, 0),
      cr(6, 2, F1, 0, 0, 0, 0),
      cr(6, 1, B2, 0, 0, 0, 0),
      cr(6, 2, F2, 0, 0, 0, 0),
      cr(6, 1, B3, 0, 0, 0, 0),
      cr(7, 1, F3, 0, 0, 0, 0),
      cr(8, 0, B4, 0, 0, 0, 0),
      cr(8, 0, F4, 0, 0, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(4, 2, AM, 1, 0, 0, 1),
      cr(5, 0, PR, 1, 0, 0, 0),
      cr(3, 3, B3, 1, 0, 0, 0),
      cr(4, 2, F3, 1, 0, 0, 0),
      cr(4, 1, B4, 1, 0, 0, 0),
      cr(4, 2, F4, 1, 0, 0, 0),
      cr(5, 0, B5, 1, 0, 0, 0),
      cr(5, 0, F5, 1, 0, 0, 0),
      cr(4, 2, AM, 1, 0, 0, 0),
      cr(5, 0, PR, 1, 0, 0, 0),
      cr(4, 1, B3, 1, 0, 0, 0),
      cr(5, 1, F3, 1, 0, 0, 0),
      cr(5, 0, B4, 1, 0, 0, 0),
      cr(5, 0, F4, 1, 0, 0, 0),
      cr(5, 0, B5, 1, 0, 0, 0),
      cr(5, 0, F5, 1, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(3, 4, B0, 0, 0, 0, 0),
      cr(5, 3, F0, 0, 0, 0, 0),
      cr(3, 4, B1, 0, 0, 0, 0),
      cr(5, 3, F1, 0, 0, 0, 0),
      cr(3, 4, B2, 0, 0, 0, 0),
      cr(5, 3, F2, 0, 0, 0, 0),
      cr(4, 3, B3, 0, 0, 0, 0),
      cr(6, 2, F3, 0, 0, 0, 0),
      cr(7, 1, B4, 0, 0, 0, 0),
      cr(6, 2, F4, 0, 0, 0, 0),
      cr(8, 0, B5, 0, 0, 0, 0),
      cr(8, 0, F5, 0, 0, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(6, 1, B0, 0, 0, 0, 0),
      cr(6, 2, F0, 0, 0, 0, 0),
      cr(6, 1, B1, 0, 0, 0, 0),
      cr(6, 2, F1, 0, 0, 0, 0),
      cr(6, 1, B2, 0, 0, 0, 0),
      cr(6, 2, F2, 0, 0, 0, 0),
      cr(6, 1, B3, 0, 0, 0, 0),
      cr(7, 1, F3, 0, 0, 0, 0),
      cr(8, 0, B4, 0, 0, 0, 0),
      cr(8, 0, F4, 0, 0, 0, 0),
      cr(8, 0, B5, 0, 0, 0, 0),
      cr(8, 0, F5, 0, 0, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(5, 3, F0, 0, 1, 0, 0),
      cr(5, 3, F1, 0, 1, 0, 0),
      cr(5, 3, F2, 0, 1, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(0, 0, 0, 0, 0, 0, 0),
      cr(6, 2, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(6, 2, F0, 0, 1, 0, 0),
      cr(6, 2, F1, 0, 1, 0, 0),
      cr(6, 2, F2, 0, 1, 0, 0),
      cr(5, 0, IA, 0, 0, 0, 0),
      cr(5, 0, IP, 0, 0, 0, 0),
      cr(8, 0, AM, 0, 0, 0, 0),
      cr(8, 0, PR, 0, 0, 0, 0),
      cr(8, 0, B0, 0, 0, 0, 0),
      cr(8, 0, F0, 0, 0, 0, 0),
      cr(8, 0, B1, 0, 0, 0, 0),
      cr(8, 0, F1, 0, 0, 0, 0),
      cr(8, 0, B2, 0, 0, 0, 0),
      cr(8, 0, F2, 0, 0, 0, 0),
      cr(8, 0, B3, 0, 0, 0, 0),
      cr(8, 0, F3, 0, 0, 0, 0),
      cr(8, 0, B4, 0, 0, 0, 0),
      cr(8, 0, F4, 0, 0, 0, 0),
      cr(8, 0, IA, 0, 0, 0, 0),
      cr(8, 0, IP, 0, 0, 0, 0)};
  private static final int[] DF_IDX = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 17, 22, 17, 24, 25, 30, 25, 32, 83, 94, 129, 142, 97, 108, 145, 158, 83, 96, 129, 144, 97, 110, 145, 160, 73, 77, 74, 77, 78, 82, 79, 82, 33, 36, 34, 37, 38, 41, 39, 42, 127, 128, 127, 128, 127, 128, 127, 128, 177, 190, 1, 16, 177, 190, 1, 16, 45, 56, 45, 58, 59, 70, 59, 72, 161, 166, 162, 166, 169, 174, 170, 174, 111, 116, 111, 118, 119, 124, 119, 126, 161, 168, 162, 168, 169, 176, 170, 176, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0};

  private final byte[] rom;
  private final Output output;
  private final short[] scratch = new short[SCBUF_SIZE];
  private int scHead;
  private int scTail;
  private int soundCurrent;

  private int rpt;
  private int cnt;
  private int per;
  private int rng = 1;
  private int amp;
  private final short[] fCoef = new short[6];
  private final short[] bCoef = new short[6];
  private final short[][] z = new short[6][2];
  private final int[] r = new int[16];
  private boolean interp;

  private boolean silent = true;
  private int lrq = 1;
  private int ald;
  private int pc;
  private int stack;
  private boolean fifoSel;
  private boolean halted = true;
  private int mode;
  private int page = 0x1000 << 3;
  private int fifoHead;
  private int fifoTail;
  private int fifoBitp;
  private final int[] fifo = new int[64];

  private int clock = 3120000;
  private int clockPerSamp = 350;

  /** @param rom the two K of its mask ROM, which it sees twice over as the page it speaks from */
  Sp0256(byte[] rom, Output output) {
    this.rom = rom;
    this.output = output;
    rpt = -1;
  }

  /** What a reset of the chip puts back: everything but its time and its clock. */
  void reset() {
    fifoHead = fifoTail = fifoBitp = 0;
    rpt = cnt = per = amp = 0;
    java.util.Arrays.fill(fCoef, (short) 0);
    java.util.Arrays.fill(bCoef, (short) 0);
    for (short[] stage : z) java.util.Arrays.fill(stage, (short) 0);
    java.util.Arrays.fill(r, 0);
    interp = false;
    halted = true;
    rpt = -1;
    rng = 1;
    lrq = 1;
    ald = 0;
    pc = 0;
    stack = 0;
    fifoSel = false;
    mode = 0;
    page = 0x1000 << 3;
    silent = true;
  }

  /**
   * The crystal it runs on, which is what the µSpeech's intonation changes. A sample is 312 of
   * its cycles, which on a 3.5 MHz processor is 358 T-states at 3.05 MHz and 334 at 3.26 MHz.
   */
  void clock(int hz, long processorSpeed) {
    clock = hz;
    int samples = (int) (hz / 312.0);
    clockPerSamp = (int) (processorSpeed / samples);
  }

  void changeClock(long now, int hz, long processorSpeed) {
    if (clock != hz) {
      runTo((int) now);
      clock(hz, processorSpeed);
    }
  }

  void play(long now, int allophone) {
    runTo((int) now);
    if (lrq == 0) return;
    lrq = 0;
    ald = (0xff & allophone) << 4;
  }

  boolean busy(long now) {
    runTo((int) now);
    return lrq == 0;
  }

  /** The frame is over: what it said up to its end goes out, and its time starts again with the next. */
  void endFrame(int frameTstates) {
    runTo(frameTstates);
    soundCurrent -= frameTstates;
  }

  private void runTo(int t) {
    while (soundCurrent < t) {
      if (run(Math.min(t - soundCurrent, 14934)) == 0) return;
    }
  }

  private int run(int len) {
    int now = soundCurrent;
    int until = now + len;
    if (until <= soundCurrent) return 0;
    while (soundCurrent < until) {
      while (scHead > SCBUF_SIZE && scTail > SCBUF_SIZE) {
        scHead -= SCBUF_SIZE;
        scTail -= SCBUF_SIZE;
      }
      scTail = scHead;
      int samples = (until - soundCurrent + clockPerSamp - 1) / clockPerSamp;
      int didSamp = 0;
      int oldIdx = scHead;
      if (samples > 0) {
        do {
          if (rpt <= 0 && cnt <= 0) micro();
          int doSamp = samples - didSamp;
          if (scHead + doSamp - scTail > SCBUF_SIZE) doSamp = scTail + SCBUF_SIZE - scHead;
          if (doSamp == 0) break;
          if (silent && rpt <= 0 && cnt <= 0) {
            for (int x = 0, y = scHead; x < doSamp; x++) scratch[y++ & SCBUF_MASK] = 0;
            scHead += doSamp;
            didSamp += doSamp;
          } else {
            didSamp += lpc12Update(doSamp);
          }
        } while ((rpt > 0 || cnt > 0) && samples > didSamp);
      }
      for (int i = 0; i < didSamp; i++) {
        output.sample(soundCurrent + (long) i * clockPerSamp, scratch[(i + oldIdx) & SCBUF_MASK]);
      }
      soundCurrent += didSamp * clockPerSamp;
    }
    return soundCurrent < now ? 0 : soundCurrent - now;
  }

  private static short limit(short s) {
    return s > 8191 ? 8191 : s < -8192 ? -8192 : s;
  }

  /** Three bits of exponent and five of mantissa. */
  private static int ampDecode(int a) {
    return (a & 0x1f) << ((a & 0xe0) >> 5);
  }

  private int lpc12Update(int numSamp) {
    int oidx = scHead;
    int i;
    for (i = 0; i < numSamp; i++) {
      boolean doInt = false;
      short samp = 0;
      int bit = rng & 1;
      rng = rng >>> 1 ^ (bit != 0 ? 0x4001 : 0);
      if (--cnt <= 0) {
        if (rpt-- <= 0) {
          cnt = rpt = 0;
          break;
        }
        cnt = per != 0 ? per : PER_NOISE;
        samp = (short) amp;
        doInt = interp;
      }
      if (per == 0) samp = (short) (bit != 0 ? -amp : amp);
      if (doInt) {
        r[0] = r[0] + r[14] & 0xff;
        r[1] = r[1] + r[15] & 0xff;
        amp = ampDecode(r[0]);
        per = r[1];
      }
      for (int j = 0; j < 6; j++) {
        samp = (short) (samp + (bCoef[j] * z[j][1] >> 9));
        samp = (short) (samp + (fCoef[j] * z[j][0] >> 8));
        z[j][1] = z[j][0];
        z[j][0] = samp;
      }
      scratch[oidx++ & SCBUF_MASK] = (short) (limit(samp) * 4);
    }
    scHead = oidx;
    return i;
  }

  private static short iq(int x) {
    return (x & 0x80) != 0 ? QTBL[0x7f & -x] : (short) -QTBL[x];
  }

  private void lpc12Regdec() {
    amp = ampDecode(r[0]);
    cnt = 0;
    per = r[1];
    for (int i = 0; i < 6; i++) {
      bCoef[i] = iq(r[2 + 2 * i]);
      fCoef[i] = iq(r[3 + 2 * i]);
    }
    interp = r[14] != 0 || r[15] != 0;
  }

  /** Up to eight bits at the program counter, which counts bits, or from the FIFO. */
  private int getb(int len) {
    int data;
    if (fifoSel) {
      int d0 = fifo[fifoTail & 63], d1 = fifo[fifoTail + 1 & 63];
      data = (d1 << 10 | d0) >>> fifoBitp;
      fifoBitp += len;
      if (fifoBitp >= 10) {
        fifoTail++;
        fifoBitp -= 10;
      }
    } else {
      int idx0 = pc >> 3, idx1 = pc + 8 >> 3;
      data = (romByte(idx1 >> 12, idx1 & 0xfff) << 8 | romByte(idx0 >> 12, idx0 & 0xfff)) >>> (pc & 7);
      pc += len;
    }
    return data & (1 << len) - 1;
  }

  private int romByte(int romPage, int index) {
    return romPage == 1 ? rom[index & rom.length - 1] & 0xff : 0;
  }

  /** Runs instructions until one gives the filter something to say, or the chip halts. */
  private void micro() {
    while (rpt <= 0 && cnt <= 0) {
      if (halted && lrq == 0) {
        pc = ald | 0x1000 << 3;
        fifoSel = false;
        halted = false;
        lrq = 1;
        ald = 0;
      }
      if (halted) {
        rpt = 1;
        cnt = 0;
        lrq = 1;
        ald = 0;
        return;
      }
      int immed4 = getb(4);
      int opcode = getb(4);
      int repeat = 0;
      boolean ctrlXfer = false;
      switch (opcode) {
        case 0x0 -> {
          if (immed4 != 0) {
            page = Integer.reverse(immed4) >>> 13;
          } else {
            int btrg = stack;
            stack = 0;
            if (btrg == 0) {
              halted = true;
              pc = 0;
            } else {
              pc = btrg;
            }
            ctrlXfer = true;
          }
        }
        case 0xe, 0xd -> {
          int btrg = page | Integer.reverse(immed4) >>> 17 | Integer.reverse(getb(8)) >>> 21;
          ctrlXfer = true;
          if (opcode == 0xd) stack = pc + 7 & ~7;
          pc = btrg;
        }
        case 0x1 -> mode = (immed4 & 8) >> 2 | immed4 & 4 | (immed4 & 3) << 4;
        default -> repeat = immed4 | mode & 0x30;
      }
      if (opcode != 1) mode &= 0xf;
      if (ctrlXfer) {
        fifoSel = pc == FIFO_ADDR;
        if (fifoSel && fifoBitp != 0) {
          if (fifoTail < fifoHead) fifoTail++;
          fifoBitp = 0;
        }
        continue;
      }
      if (repeat == 0) continue;
      rpt = repeat;
      for (short[] stage : z) stage[0] = stage[1] = 0;
      int idx0 = DF_IDX[opcode << 3 | mode & 6], idx1 = DF_IDX[(opcode << 3 | mode & 6) + 1];
      if ((mode & 2) == 0) r[F5] = r[B5] = 0;
      for (int i = idx0; i <= idx1; i++) {
        int cr = DATAFMT[i];
        int len = cr & 15, shf = cr >> 4 & 15, prm = cr >> 8 & 15;
        if ((cr & CR_CLRL) != 0) r[F0] = r[B0] = r[F1] = r[B1] = r[F2] = r[B2] = 0;
        if (len == 0) continue;
        byte value = (byte) getb(len);
        if ((cr & CR_DELTA) != 0 && (value & 1 << len - 1) != 0) value = (byte) (value | -(1 << len));
        if (shf != 0) value = (byte) (value < 0 ? -(-value << shf) : value << shf);
        silent = false;
        if ((cr & CR_FIELD) != 0) {
          r[prm] = (r[prm] & ~(-1 << shf) | value) & 0xff;
        } else if ((cr & CR_DELTA) != 0) {
          r[prm] = r[prm] + value & 0xff;
        } else {
          r[prm] = value & 0xff;
        }
      }
      if (opcode != 0x1 && opcode != 0x2 && opcode != 0x3) {
        r[IA] = 0;
        r[IP] = 0;
      }
      if (opcode == 0xf) {
        silent = true;
        r[AM] = 0;
        r[PR] = PER_PAUSE;
      }
      lpc12Regdec();
      break;
    }
  }
}

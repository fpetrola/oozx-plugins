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

package com.fpetrola.oozx.speccy.modules.tape.cassette;

import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.*;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Step.Level;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.InflaterInputStream;

import static com.fpetrola.oozx.speccy.modules.tape.cassette.Step.Level.*;

/**
 * How each block that sounds sounds: steps of the level and waits, in T-states, as the ROM and the
 * TZX specification time them. The ones that only say how the tape is played are the player's.
 */
public final class Sounds {

  public static final int PILOT = 2168;
  public static final int SYNC1 = 667;
  public static final int SYNC2 = 735;
  public static final int ZERO = 855;
  public static final int ONE = 1710;
  public static final int HEADER_PULSES = 8063;
  public static final int DATA_PULSES = 3223;
  /** A second, which a TAP leaves after every block. */
  public static final int SECOND = 3_500_000;
  /** The millisecond the TZX specification leaves after the last pulse of data, before its pause. */
  private static final int LAST_PULSE = 3500;

  private Sounds() {
  }

  /** A header's flag is below 0x80 and has the long pilot; data has the short one. */
  public static int pilotPulsesOf(byte[] data) {
    return data.length > 0 && (data[0] & 0xff) < 0x80 ? HEADER_PULSES : DATA_PULSES;
  }

  /** A pause in milliseconds as T-states, and at the end of the tape never long nor none: a millisecond. */
  public static int pause(int milliseconds, boolean last) {
    int pause = last && (milliseconds > 1000 || milliseconds == 0) ? 1 : milliseconds;
    return pause * (SECOND / 1000);
  }

  /** A block of a TAP: a high level first, the pilot, the syncs, the bytes, and a second. */
  public static Sound tap(TapData block) {
    Steps steps = new Steps();
    steps.add(Step.of(HIGH, PILOT));
    steps.repeat(pilotPulsesOf(block.data()), Step.of(TOGGLE, PILOT));
    steps.add(Step.of(TOGGLE, SYNC1));
    steps.add(Step.of(TOGGLE, SYNC2));
    return then(then(steps, new Bits(block.data(), ZERO, ONE, 8, false)), Steps.of(Step.of(TOGGLE, SECOND)));
  }

  /** 0x10 and 0x11: the pilot starts without an edge, and the last pulse of data is followed by a millisecond and the pause. */
  public static Sound data(int pilot, int pilotPulses, int sync1, int sync2, int zero, int one, int usedBits, int pause, byte[] data) {
    Steps steps = new Steps();
    if (pilotPulses > 0) {
      steps.add(Step.of(KEEP, pilot));
      steps.repeat(pilotPulses - 1, Step.of(TOGGLE, pilot));
      steps.add(Step.of(TOGGLE, sync1));
    } else {
      steps.add(Step.of(KEEP, sync1));
    }
    steps.add(Step.of(TOGGLE, sync2));
    if (data.length == 0) {
      return steps.then(Steps.of(Step.of(REST, pause)));
    }
    return then(then(steps, new Bits(data, zero, one, usedBits, false)), end(pause));
  }

  /** 0x14: bits alone, the first of which starts without an edge. */
  public static Sound pureData(PureData block, int pause) {
    if (block.data().length == 0) {
      return end(pause);
    }
    return new Bits(block.data(), block.zero(), block.one(), block.usedBits(), true).then(end(pause));
  }

  /** 0x12: a tone, started without an edge. */
  public static Sound tone(PureTone block) {
    Steps steps = new Steps();
    if (block.pulses() > 0) {
      steps.add(Step.of(KEEP, block.length()));
      steps.repeat(block.pulses() - 1, Step.of(TOGGLE, block.length()));
      steps.add(Step.now(TOGGLE));
    }
    return steps;
  }

  /** 0x13: pulses, the first started without an edge. */
  public static Sound pulses(PulseSequence block) {
    Steps steps = new Steps();
    int[] lengths = block.lengths();
    for (int at = 0; at < lengths.length; at++) {
      steps.add(Step.of(at == 0 ? KEEP : TOGGLE, lengths[at]));
    }
    if (lengths.length > 0) {
      steps.add(Step.now(TOGGLE));
    }
    return steps;
  }

  /** 0x15: the level itself, a sample a bit, a run of equal samples a step. */
  public static Sound direct(DirectRecording block, int pause) {
    if (block.samples().length == 0) {
      return pause > 0 ? Steps.of(Step.of(REST, pause)) : new Steps();
    }
    return new Direct(block).then(end(pause));
  }

  /** 0x18: a CSW inside a TZX, whose first pulse starts without an edge. */
  public static Sound csw(CswRecording block, int pause) {
    float perSample = 3_500_000.0f / block.sampleRate();
    Sound pulses = block.compression() == 0x02 ? new PackedPulses(block.data(), perSample) : new Pulses(block.data(), perSample);
    return Steps.of(Step.now(TOGGLE)).then(new Ending(pulses, Step.of(REST, pause)));
  }

  /** A whole CSW file: its level first, and then its pulses to the last. */
  public static Sound cswTape(CswTape tape) {
    float perSample = 3_500_000.0f / tape.sampleRate();
    Level first = tape.startsLow() ? LOW : HIGH;
    if (tape.major() != 0x01 && tape.compression() == 0x02) {
      return Steps.of(Step.of(first, 1)).then(new PackedPulses(tape.data(), perSample));
    }
    return Steps.of(Step.now(first)).then(new Pulses(tape.data(), perSample));
  }

  /**
   * 0x19: symbols, each a few pulses, as libspectrum plays them. The first pulse of a symbol
   * starts as the symbol says - an edge, none, low or high - the rest with an edge, and a length of
   * 0 ends it. First the pilot, runs of a symbol so many times; then the data, symbols packed as
   * bits; then the pause. A block that cannot be made sense of sounds as nothing, as before.
   */
  public static Sound generalized(GeneralizedData block, boolean last) {
    try {
      return Generalized.of(block.body(), last);
    } catch (RuntimeException notASymbolTable) {
      return new Steps();
    }
  }

  static final class Generalized {
    private Generalized() {
    }

    static Sound of(byte[] body, boolean last) {
      java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(body).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      int pauseMs = in.getShort() & 0xffff;
      int totp = in.getInt();
      int npp = in.get() & 0xff;
      int asp = in.get() & 0xff;
      int totd = in.getInt();
      int npd = in.get() & 0xff;
      int asd = in.get() & 0xff;
      if (asp == 0) asp = 256;
      if (asd == 0) asd = 256;
      Steps steps = new Steps();
      if (totp > 0) {
        Table pilot = Table.read(in, asp, npp);
        for (int run = 0; run < totp; run++) {
          int symbol = in.get() & 0xff;
          int repeats = in.getShort() & 0xffff;
          for (int time = 0; time < repeats; time++) symbol(steps, pilot.lengths()[symbol], pilot.flags()[symbol]);
        }
      }
      if (totd > 0) {
        Table data = Table.read(in, asd, npd);
        int bits = 32 - Integer.numberOfLeadingZeros(asd - 1);
        if (bits == 0) bits = 1;
        int bit = 0;
        for (int at = 0; at < totd; at++) {
          int symbol = 0;
          for (int b = 0; b < bits; b++, bit++) {
            int value = in.get(in.position() + bit / 8) >> (7 - bit % 8) & 1;
            symbol = symbol << 1 | value;
          }
          symbol(steps, data.lengths()[symbol], data.flags()[symbol]);
        }
      }
      return then(steps, end(pause(pauseMs, last)));
    }

    /** A symbol table: for each symbol how its first pulse starts, and the lengths of its pulses. */
    record Table(int[] flags, int[][] lengths) {
      static Table read(java.nio.ByteBuffer in, int count, int pulses) {
        int[] flags = new int[count];
        int[][] lengths = new int[count][pulses];
        for (int symbol = 0; symbol < count; symbol++) {
          flags[symbol] = in.get() & 0x03;
          for (int pulse = 0; pulse < pulses; pulse++) lengths[symbol][pulse] = in.getShort() & 0xffff;
        }
        return new Table(flags, lengths);
      }
    }

    private static void symbol(Steps steps, int[] lengths, int flags) {
      Level first = switch (flags) {
        case 1 -> KEEP;
        case 2 -> LOW;
        case 3 -> HIGH;
        default -> TOGGLE;
      };
      for (int pulse = 0; pulse < lengths.length; pulse++) {
        steps.add(Step.of(pulse == 0 ? first : TOGGLE, lengths[pulse]));
        if (pulse + 1 < lengths.length && lengths[pulse + 1] == 0) break;
      }
    }
  }

  /** PZX pulses: low first, each pulse its length and then an edge. */
  public static Sound pulseRun(PulseRun block) {
    Steps steps = Steps.of(Step.now(LOW));
    boolean first = true;
    for (int at = 0; at < block.lengths().length; at++) {
      for (int time = 0; time < block.repeats()[at]; time++) {
        steps.add(Step.of(first ? KEEP : TOGGLE, block.lengths()[at]));
        first = false;
      }
    }
    if (!first) steps.add(Step.now(TOGGLE));
    return steps;
  }

  /** PZX data: from its level, each bit the pulses of a 0 or a 1, the tail, and an edge after the last. */
  public static Sound encodedData(EncodedData block) {
    Steps steps = Steps.of(Step.now(block.startsHigh() ? HIGH : LOW));
    boolean first = true;
    for (int bit = 0; bit < block.bits(); bit++) {
      boolean one = (block.data()[bit / 8] >> (7 - bit % 8) & 1) != 0;
      for (int length : one ? block.one() : block.zero()) {
        steps.add(Step.of(first ? KEEP : TOGGLE, length));
        first = false;
      }
    }
    if (block.tail() > 0) {
      steps.add(Step.of(first ? KEEP : TOGGLE, block.tail()));
      first = false;
    }
    if (!first) steps.add(Step.now(TOGGLE));
    return steps;
  }

  /** PZX pause: the level it says, for as long as it says. */
  public static Sound silence(Silence block) {
    return Steps.of(Step.of(block.high() ? HIGH : LOW, block.tstates()));
  }

  /** A silence, at the level a silent line rests at. */
  public static Sound silence(int tstates) {
    return Steps.of(Step.of(REST, tstates));
  }

  /** After the last pulse of data: an edge, a millisecond, and the pause; or, without a pause, the edge alone. */
  private static Steps end(int pause) {
    return pause == 0 ? Steps.of(Step.now(TOGGLE)) : Steps.of(Step.of(TOGGLE, LAST_PULSE), Step.of(REST, pause));
  }

  /** Steps known in advance. */
  static final class Steps implements Sound {
    private final Deque<Step> steps = new ArrayDeque<>();
    private int count;

    static Steps of(Step... steps) {
      Steps all = new Steps();
      for (Step step : steps) all.add(step);
      return all;
    }

    void add(Step step) {
      steps.add(step);
    }

    void repeat(int times, Step step) {
      for (int at = 0; at < times; at++) steps.add(step);
    }

    public Step next() {
      return steps.poll();
    }

    Sound then(Sound after) {
      return Sounds.then(this, after);
    }
  }

  static Sound then(Sound first, Sound after) {
    return new Sound() {
      boolean second;

      public Step next() {
        if (!second) {
          Step step = first.next();
          if (step != null) return step;
          second = true;
        }
        return after.next();
      }

      public int played() {
        return first.played() + (second ? after.played() : 0);
      }
    };
  }

  /** Bytes, most significant bit first, a bit two pulses; of the last byte only so many bits. */
  static final class Bits implements Sound {
    private final byte[] data;
    private final int zero;
    private final int one;
    private final int lastBits;
    private boolean firstKeeps;
    private int at;
    private int bit;
    private boolean secondHalf;

    Bits(byte[] data, int zero, int one, int usedBits, boolean firstKeeps) {
      this.data = data;
      this.zero = zero;
      this.one = one;
      this.lastBits = usedBits >= 1 && usedBits < 8 ? usedBits : 8;
      this.firstKeeps = firstKeeps;
    }

    public Step next() {
      if (at >= data.length) return null;
      int length = (data[at] & (0x80 >>> bit)) == 0 ? zero : one;
      Level level = firstKeeps ? KEEP : TOGGLE;
      firstKeeps = false;
      if (!secondHalf) {
        secondHalf = true;
        return Step.of(level, length);
      }
      secondHalf = false;
      bit++;
      if (bit == (at == data.length - 1 ? lastBits : 8)) {
        bit = 0;
        at++;
      }
      return Step.of(TOGGLE, length);
    }

    public int played() {
      return at;
    }

    Sound then(Sound after) {
      return Sounds.then(this, after);
    }
  }

  /** A direct recording: each step sets the level of a run of equal samples and waits for all of it. */
  static final class Direct implements Sound {
    private final byte[] samples;
    private final int perSample;
    private final int lastBits;
    private int at;
    private int mask = 0x80;
    private int left;
    private boolean over;

    Direct(DirectRecording block) {
      this.samples = block.samples();
      this.perSample = block.tstatesPerSample();
      this.lastBits = block.usedBits();
      this.left = samples.length;
    }

    public Step next() {
      if (over) return null;
      boolean high = (samples[at] & mask) != 0;
      int wait = 0;
      while (((samples[at] & mask) != 0) == high) {
        wait += perSample;
        mask >>>= 1;
        if (mask == 0) {
          mask = 0x80;
          at++;
          if (--left == 0) {
            over = true;
            break;
          }
        } else if (left == 1 && lastBits < 8 && mask == (0x80 >>> lastBits)) {
          over = true;
          at++;
          break;
        }
      }
      return Step.of(high ? HIGH : LOW, wait);
    }

    public int played() {
      return at;
    }

    Sound then(Sound after) {
      return Sounds.then(this, after);
    }
  }

  /** CSW pulses, run-length coded: a byte a pulse, or a zero and four bytes for a long one. */
  static final class Pulses implements Sound {
    private final byte[] data;
    private final float perSample;
    private int at;

    Pulses(byte[] data, float perSample) {
      this.data = data;
      this.perSample = perSample;
    }

    public Step next() {
      if (at >= data.length) return null;
      int samples = data[at++] & 0xff;
      if (samples == 0) {
        if (at + 4 > data.length) return null;
        samples = (data[at] & 0xff) | (data[at + 1] & 0xff) << 8 | (data[at + 2] & 0xff) << 16 | (data[at + 3] & 0xff) << 24;
        at += 4;
      }
      int wait = samples;
      wait *= perSample;
      return Step.of(TOGGLE, wait);
    }

    public int played() {
      return at;
    }
  }

  /** CSW pulses packed with zlib: the same, unpacked as they are played. */
  static final class PackedPulses implements Sound {
    private final InflaterInputStream in;
    private final float perSample;
    private boolean over;

    PackedPulses(byte[] data, float perSample) {
      this.in = new InflaterInputStream(new ByteArrayInputStream(data));
      this.perSample = perSample;
    }

    public Step next() {
      if (over) return null;
      try {
        int samples = in.read();
        if (samples == 0) {
          byte[] four = in.readNBytes(4);
          samples = four.length == 4 ? (four[0] & 0xff) | (four[1] & 0xff) << 8 | (four[2] & 0xff) << 16 | (four[3] & 0xff) << 24 : -1;
        }
        if (samples < 0) {
          over = true;
          in.close();
          return null;
        }
        int wait = samples;
        wait *= perSample;
        return Step.of(TOGGLE, wait);
      } catch (IOException broken) {
        over = true;
        return null;
      }
    }
  }

  /** Pulses that, when there are none left, end with one more step: the pause of a CSW inside a TZX. */
  static final class Ending implements Sound {
    private final Sound pulses;
    private Step last;

    Ending(Sound pulses, Step last) {
      this.pulses = pulses;
      this.last = last;
    }

    public Step next() {
      Step step = pulses.next();
      if (step != null) return step;
      Step once = last;
      last = null;
      return once;
    }

    public int played() {
      return pulses.played();
    }
  }
}

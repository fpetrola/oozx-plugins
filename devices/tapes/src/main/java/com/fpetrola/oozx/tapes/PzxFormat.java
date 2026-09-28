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

package com.fpetrola.oozx.tapes;

import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassette;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.*;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import dev.crystal.plugins.api.Answers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PZX: blocks, each a tag of four letters, a length, and its body, as libspectrum reads them. A
 * tag nobody knows is passed over, as the format asks.
 */
@Answers("pzx")
public final class PzxFormat implements TapeFormat {

  /** How the body of each tag becomes a block. */
  private interface Body {
    CassetteBlock read(Cursor body);
  }

  private static final Map<String, Body> BY_TAG = Map.of(
      "PZXT", body -> new PzxInfo(body.rest()),
      "PULS", PzxFormat::pulses,
      "DATA", PzxFormat::data,
      "PAUS", body -> {
        int pause = body.u32();
        return new Silence(pause & 0x7fffffff, (pause & 0x80000000) != 0);
      },
      "BRWS", body -> new Text(new String(body.rest(), StandardCharsets.ISO_8859_1)),
      "STOP", body -> body.u16() == 1 ? new StopIf48K() : new Pause(0));

  @Override
  public boolean reads(File file) {
    return file.getName().toLowerCase().endsWith(".pzx");
  }

  @Override
  public boolean writes(File file) {
    return reads(file);
  }

  @Override
  public Cassette read(byte[] file) throws TapeRefused {
    try {
      Cursor in = Cursor.over(file);
      List<CassetteBlock> blocks = new ArrayList<>();
      boolean first = true;
      while (in.left() > 0) {
        String tag = new String(in.take(4), StandardCharsets.ISO_8859_1);
        if (first && !tag.equals("PZXT")) {
          throw new TapeRefused("not a PZX: it does not start with PZXT");
        }
        first = false;
        Cursor body = Cursor.over(in.take(in.u32()));
        Body known = BY_TAG.get(tag);
        if (known != null) {
          blocks.add(known.read(body));
        }
      }
      return new Cassette(blocks);
    } catch (Refused refused) {
      throw new TapeRefused("a PZX that could not be read: " + refused.getMessage(), refused);
    }
  }

  /** Pulses: a length, or a count of repeats with the top bit and then the length; a long length in two words. */
  private static CassetteBlock pulses(Cursor body) {
    List<int[]> read = new ArrayList<>();
    while (body.left() > 0) {
      int repeats = 1;
      int length = body.u16();
      if (length > 0x8000) {
        repeats = length & 0x7fff;
        length = body.u16();
      }
      if (length >= 0x8000) {
        length = (length & 0x7fff) << 16 | body.u16();
      }
      read.add(new int[]{repeats, length});
    }
    if (read.isEmpty()) {
      throw new Refused("a pulse block without pulses");
    }
    return new PulseRun(read.stream().mapToInt(pulse -> pulse[0]).toArray(), read.stream().mapToInt(pulse -> pulse[1]).toArray());
  }

  private static CassetteBlock data(Cursor body) {
    int count = body.u32();
    boolean high = (count & 0x80000000) != 0;
    int bits = count & 0x7fffffff;
    int tail = body.u16();
    int zeros = body.u8();
    int ones = body.u8();
    int[] zero = new int[zeros];
    int[] one = new int[ones];
    for (int at = 0; at < zeros; at++) zero[at] = body.u16();
    for (int at = 0; at < ones; at++) one[at] = body.u16();
    return new EncodedData(bits, high, tail, zero, one, body.take((bits + 7) / 8));
  }

  @Override
  public byte[] write(Cassette cassette) throws TapeRefused {
    Sink out = new Sink();
    Writer writer = new Writer(out);
    for (CassetteBlock block : cassette.blocks()) {
      if (!block.accept(writer)) {
        throw new TapeRefused("a PZX has no block for " + block.getClass().getSimpleName());
      }
    }
    return out.toBytes();
  }

  /** Each kind of block PZX has, written with its tag. */
  private record Writer(Sink out) implements CassetteBlock.Visitor<Boolean> {
    private boolean block(String tag, Sink body) {
      byte[] bytes = body.toBytes();
      out.bytes(tag.getBytes(StandardCharsets.ISO_8859_1)).u32(bytes.length).bytes(bytes);
      return true;
    }

    public Boolean pzxInfo(PzxInfo b) { return block("PZXT", new Sink().bytes(b.body())); }

    public Boolean pulseRun(PulseRun b) {
      Sink body = new Sink();
      for (int at = 0; at < b.lengths().length; at++) {
        int length = b.lengths()[at];
        if (b.repeats()[at] > 1) body.u16(0x8000 | b.repeats()[at]);
        if (length >= 0x8000) body.u16(0x8000 | length >> 16).u16(length & 0xffff);
        else body.u16(length);
      }
      return block("PULS", body);
    }

    public Boolean encodedData(EncodedData b) {
      Sink body = new Sink().u32(b.bits() | (b.startsHigh() ? 0x80000000 : 0)).u16(b.tail()).u8(b.zero().length).u8(b.one().length);
      for (int length : b.zero()) body.u16(length);
      for (int length : b.one()) body.u16(length);
      return block("DATA", body.bytes(b.data()));
    }

    public Boolean silence(Silence b) { return block("PAUS", new Sink().u32(b.tstates() | (b.high() ? 0x80000000 : 0))); }
    public Boolean text(Text b) { return block("BRWS", new Sink().bytes(b.text().getBytes(StandardCharsets.ISO_8859_1))); }
    public Boolean stopIf48K(StopIf48K b) { return block("STOP", new Sink().u16(1)); }
    public Boolean pause(Pause b) { return b.milliseconds() == 0 && block("STOP", new Sink().u16(0)); }

    public Boolean tapData(TapData b) { return false; }
    public Boolean standardData(StandardData b) { return false; }
    public Boolean turboData(TurboData b) { return false; }
    public Boolean pureTone(PureTone b) { return false; }
    public Boolean pulseSequence(PulseSequence b) { return false; }
    public Boolean pureData(PureData b) { return false; }
    public Boolean directRecording(DirectRecording b) { return false; }
    public Boolean cswRecording(CswRecording b) { return false; }
    public Boolean generalizedData(GeneralizedData b) { return false; }
    public Boolean groupStart(GroupStart b) { return false; }
    public Boolean groupEnd(GroupEnd b) { return false; }
    public Boolean jump(Jump b) { return false; }
    public Boolean loopStart(LoopStart b) { return false; }
    public Boolean loopEnd(LoopEnd b) { return false; }
    public Boolean callSequence(CallSequence b) { return false; }
    public Boolean returnFromSequence(ReturnFromSequence b) { return false; }
    public Boolean select(Select b) { return false; }
    public Boolean signalLevel(SignalLevel b) { return false; }
    public Boolean message(Message b) { return false; }
    public Boolean archiveInfo(ArchiveInfo b) { return false; }
    public Boolean hardwareInfo(HardwareInfo b) { return false; }
    public Boolean customInfo(CustomInfo b) { return false; }
    public Boolean glue(Glue b) { return false; }
    public Boolean cswTape(CswTape b) { return false; }
  }
}

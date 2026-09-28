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
import com.fpetrola.oozx.formats.Sequence;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassette;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.*;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import dev.crystal.plugins.api.Answers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * TZX: a signature, a version, and blocks, each an id and its fields. Every kind of block is a
 * sequence declared once, read by its id and written by what it is.
 */
@Answers("tzx")
public final class TzxFormat implements TapeFormat {

  static final byte[] SIGNATURE = {'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1a};
  static final int MAJOR = 1;
  static final int MINOR = 20;

  static final Sequence<StandardData> STANDARD = Sequence.<StandardData>of()
      .u16(StandardData::pause).data(2, StandardData::data)
      .make(r -> new StandardData(r.i(0), r.bytes(1)));
  static final Sequence<TurboData> TURBO = Sequence.<TurboData>of()
      .u16(TurboData::pilot).u16(TurboData::sync1).u16(TurboData::sync2).u16(TurboData::zero).u16(TurboData::one)
      .u16(TurboData::pilotPulses).u8(TurboData::usedBits).u16(TurboData::pause).data(3, TurboData::data)
      .make(r -> new TurboData(r.i(0), r.i(1), r.i(2), r.i(3), r.i(4), r.i(5), r.i(6), r.i(7), r.bytes(8)));
  static final Sequence<PureTone> TONE = Sequence.<PureTone>of()
      .u16(PureTone::length).u16(PureTone::pulses)
      .make(r -> new PureTone(r.i(0), r.i(1)));
  static final Sequence<PulseSequence> PULSES = Sequence.<PulseSequence>of()
      .numbers(1, 2, false, PulseSequence::lengths)
      .make(r -> new PulseSequence(r.ints(0)));
  static final Sequence<PureData> PURE = Sequence.<PureData>of()
      .u16(PureData::zero).u16(PureData::one).u8(PureData::usedBits).u16(PureData::pause).data(3, PureData::data)
      .make(r -> new PureData(r.i(0), r.i(1), r.i(2), r.i(3), r.bytes(4)));
  static final Sequence<DirectRecording> DIRECT = Sequence.<DirectRecording>of()
      .u16(DirectRecording::tstatesPerSample).u16(DirectRecording::pause).u8(DirectRecording::usedBits)
      .data(3, DirectRecording::samples)
      .make(r -> new DirectRecording(r.i(0), r.i(1), r.i(2), r.bytes(3)));
  static final Sequence<CswRecording> CSW = Sequence.<CswRecording>of()
      .within(4, Sequence.<CswRecording>of().u16(CswRecording::pause).u24(CswRecording::sampleRate)
          .u8(CswRecording::compression).u32(CswRecording::pulses).rest(CswRecording::data))
      .make(r -> new CswRecording(r.i(0), r.i(1), r.i(2), r.i(3), r.bytes(4)));
  static final Sequence<GeneralizedData> GENERALIZED = Sequence.<GeneralizedData>of()
      .data(4, GeneralizedData::body).make(r -> new GeneralizedData(r.bytes(0)));
  static final Sequence<Pause> PAUSE = Sequence.<Pause>of()
      .u16(Pause::milliseconds).make(r -> new Pause(r.i(0)));
  static final Sequence<GroupStart> GROUP_START = Sequence.<GroupStart>of()
      .text8(GroupStart::name).make(r -> new GroupStart(r.text(0)));
  static final Sequence<GroupEnd> GROUP_END = Sequence.<GroupEnd>of().make(r -> new GroupEnd());
  static final Sequence<Jump> JUMP = Sequence.<Jump>of().s16(Jump::offset).make(r -> new Jump(r.i(0)));
  static final Sequence<LoopStart> LOOP_START = Sequence.<LoopStart>of()
      .u16(LoopStart::repetitions).make(r -> new LoopStart(r.i(0)));
  static final Sequence<LoopEnd> LOOP_END = Sequence.<LoopEnd>of().make(r -> new LoopEnd());
  static final Sequence<CallSequence> CALL = Sequence.<CallSequence>of()
      .numbers(2, 2, true, CallSequence::offsets).make(r -> new CallSequence(r.ints(0)));
  static final Sequence<ReturnFromSequence> RETURN = Sequence.<ReturnFromSequence>of().make(r -> new ReturnFromSequence());
  static final Sequence<Select> SELECT = Sequence.<Select>of().data(2, Select::body).make(r -> new Select(r.bytes(0)));
  static final Sequence<StopIf48K> STOP_48 = Sequence.<StopIf48K>of()
      .within(4, Sequence.<StopIf48K>of()).make(r -> new StopIf48K());
  static final Sequence<SignalLevel> LEVEL = Sequence.<SignalLevel>of()
      .within(4, Sequence.<SignalLevel>of().u8(SignalLevel::level)).make(r -> new SignalLevel(r.i(0)));
  static final Sequence<Text> TEXT = Sequence.<Text>of().text8(Text::text).make(r -> new Text(r.text(0)));
  static final Sequence<Message> MESSAGE = Sequence.<Message>of()
      .u8(Message::seconds).text8(Message::text).make(r -> new Message(r.i(0), r.text(1)));
  static final Sequence<ArchiveInfo> ARCHIVE = Sequence.<ArchiveInfo>of()
      .data(2, ArchiveInfo::body).make(r -> new ArchiveInfo(r.bytes(0)));
  static final Sequence<HardwareInfo> HARDWARE = Sequence.<HardwareInfo>of()
      .records(1, 3, HardwareInfo::body).make(r -> new HardwareInfo(r.bytes(0)));
  static final Sequence<CustomInfo> CUSTOM = Sequence.<CustomInfo>of()
      .text(16, CustomInfo::id).data(4, CustomInfo::data).make(r -> new CustomInfo(r.text(0), r.bytes(1)));
  static final Sequence<Glue> GLUE = Sequence.<Glue>of().fixed(9, Glue::body).make(r -> new Glue(r.bytes(0)));

  /** Every kind of block, by the id a file gives it. */
  static final Map<Integer, Sequence<? extends CassetteBlock>> BY_ID = Map.ofEntries(
      entry(0x10, STANDARD), entry(0x11, TURBO), entry(0x12, TONE), entry(0x13, PULSES), entry(0x14, PURE),
      entry(0x15, DIRECT), entry(0x18, CSW), entry(0x19, GENERALIZED), entry(0x20, PAUSE),
      entry(0x21, GROUP_START), entry(0x22, GROUP_END), entry(0x23, JUMP), entry(0x24, LOOP_START),
      entry(0x25, LOOP_END), entry(0x26, CALL), entry(0x27, RETURN), entry(0x28, SELECT), entry(0x2a, STOP_48),
      entry(0x2b, LEVEL), entry(0x30, TEXT), entry(0x31, MESSAGE), entry(0x32, ARCHIVE), entry(0x33, HARDWARE),
      entry(0x35, CUSTOM), entry(0x5a, GLUE));

  @Override
  public boolean reads(File file) {
    return file.getName().toLowerCase().endsWith(".tzx");
  }

  @Override
  public boolean writes(File file) {
    return reads(file);
  }

  @Override
  public Cassette read(byte[] file) throws TapeRefused {
    if (file.length == 0) {
      return new Cassette(List.of());
    }
    try {
      Cursor in = Cursor.over(file);
      if (!Arrays.equals(in.take(SIGNATURE.length), SIGNATURE)) {
        throw new TapeRefused("not a TZX: it does not start with ZXTape!");
      }
      in.skip(2);
      List<CassetteBlock> blocks = new ArrayList<>();
      while (in.left() > 0) {
        int id = in.u8();
        Sequence<? extends CassetteBlock> block = BY_ID.get(id);
        if (block == null) {
          throw new TapeRefused(String.format("a block 0x%02x, which is not one this reads", id));
        }
        blocks.add(block.read(in));
      }
      return new Cassette(blocks);
    } catch (Refused refused) {
      throw new TapeRefused("a TZX that could not be read: " + refused.getMessage(), refused);
    }
  }

  @Override
  public byte[] write(Cassette cassette) throws TapeRefused {
    Sink out = new Sink().bytes(SIGNATURE).u8(MAJOR).u8(MINOR);
    Writer writer = new Writer(out);
    for (CassetteBlock block : cassette.blocks()) {
      if (!block.accept(writer)) {
        throw new TapeRefused("a TZX has no block for " + block.getClass().getSimpleName());
      }
    }
    return out.toBytes();
  }

  /** Each kind of block, written with the id and the sequence it is read with. */
  private record Writer(Sink out) implements CassetteBlock.Visitor<Boolean> {
    private <B> boolean block(int id, Sequence<B> sequence, B block) {
      out.u8(id);
      sequence.write(block, out);
      return true;
    }

    public Boolean tapData(TapData b) { return block(0x10, STANDARD, new StandardData(1000, b.data())); }
    public Boolean standardData(StandardData b) { return block(0x10, STANDARD, b); }
    public Boolean turboData(TurboData b) { return block(0x11, TURBO, b); }
    public Boolean pureTone(PureTone b) { return block(0x12, TONE, b); }
    public Boolean pulseSequence(PulseSequence b) { return block(0x13, PULSES, b); }
    public Boolean pureData(PureData b) { return block(0x14, PURE, b); }
    public Boolean directRecording(DirectRecording b) { return block(0x15, DIRECT, b); }
    public Boolean cswRecording(CswRecording b) { return block(0x18, CSW, b); }
    public Boolean generalizedData(GeneralizedData b) { return block(0x19, GENERALIZED, b); }
    public Boolean pause(Pause b) { return block(0x20, PAUSE, b); }
    public Boolean groupStart(GroupStart b) { return block(0x21, GROUP_START, b); }
    public Boolean groupEnd(GroupEnd b) { return block(0x22, GROUP_END, b); }
    public Boolean jump(Jump b) { return block(0x23, JUMP, b); }
    public Boolean loopStart(LoopStart b) { return block(0x24, LOOP_START, b); }
    public Boolean loopEnd(LoopEnd b) { return block(0x25, LOOP_END, b); }
    public Boolean callSequence(CallSequence b) { return block(0x26, CALL, b); }
    public Boolean returnFromSequence(ReturnFromSequence b) { return block(0x27, RETURN, b); }
    public Boolean select(Select b) { return block(0x28, SELECT, b); }
    public Boolean stopIf48K(StopIf48K b) { return block(0x2a, STOP_48, b); }
    public Boolean signalLevel(SignalLevel b) { return block(0x2b, LEVEL, b); }
    public Boolean text(Text b) { return block(0x30, TEXT, b); }
    public Boolean message(Message b) { return block(0x31, MESSAGE, b); }
    public Boolean archiveInfo(ArchiveInfo b) { return block(0x32, ARCHIVE, b); }
    public Boolean hardwareInfo(HardwareInfo b) { return block(0x33, HARDWARE, b); }
    public Boolean customInfo(CustomInfo b) { return block(0x35, CUSTOM, b); }
    public Boolean glue(Glue b) { return block(0x5a, GLUE, b); }
    public Boolean cswTape(CswTape b) { return false; }
    public Boolean pulseRun(PulseRun b) { return false; }
    public Boolean encodedData(EncodedData b) { return false; }
    public Boolean silence(Silence b) { return false; }
    public Boolean pzxInfo(PzxInfo b) { return false; }
  }
}

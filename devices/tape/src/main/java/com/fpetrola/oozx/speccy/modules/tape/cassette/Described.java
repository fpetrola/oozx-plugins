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

import java.nio.charset.StandardCharsets;

/**
 * A block as a person is shown it: the id a TZX gives its kind (-1 for a TAP's block, which has
 * none), what kind it is, what is worth knowing about this one, and how many bytes it carries.
 */
public record Described(int id, String type, String details, int bytes) {

  public static Described of(CassetteBlock block) {
    return block.accept(DESCRIBING);
  }

  private static final CassetteBlock.Visitor<Described> DESCRIBING = new CassetteBlock.Visitor<>() {
    public Described tapData(TapData b) { return data(-1, "Standard data", b.data()); }
    public Described standardData(StandardData b) { return data(0x10, "Standard data", b.data()); }
    public Described turboData(TurboData b) { return new Described(0x11, "Turbo data", b.data().length + " bytes, " + b.pilotPulses() + " pilot pulses", b.data().length); }
    public Described pureTone(PureTone b) { return new Described(0x12, "Pure tone", b.pulses() + " pulses of " + b.length() + " T-states", 0); }
    public Described pulseSequence(PulseSequence b) { return new Described(0x13, "Pulses", b.lengths().length + " pulses", 0); }
    public Described pureData(PureData b) { return new Described(0x14, "Pure data", b.data().length + " bytes", b.data().length); }
    public Described directRecording(DirectRecording b) { return new Described(0x15, "Direct recording", b.samples().length + " bytes", b.samples().length); }
    public Described cswRecording(CswRecording b) { return new Described(0x18, "CSW recording", b.pulses() + " pulses at " + b.sampleRate() + " Hz", b.data().length); }
    public Described generalizedData(GeneralizedData b) { return new Described(0x19, "Generalized data", b.body().length + " bytes", b.body().length); }
    public Described pause(Pause b) { return new Described(0x20, b.milliseconds() == 0 ? "Stop the tape" : "Pause", b.milliseconds() == 0 ? "" : b.milliseconds() + " ms", 0); }
    public Described groupStart(GroupStart b) { return new Described(0x21, "Group start", b.name(), 0); }
    public Described groupEnd(GroupEnd b) { return new Described(0x22, "Group end", "", 0); }
    public Described jump(Jump b) { return new Described(0x23, "Jump", (b.offset() > 0 ? "+" : "") + b.offset() + " blocks", 0); }
    public Described loopStart(LoopStart b) { return new Described(0x24, "Loop start", b.repetitions() + " times", 0); }
    public Described loopEnd(LoopEnd b) { return new Described(0x25, "Loop end", "", 0); }
    public Described callSequence(CallSequence b) { return new Described(0x26, "Call sequence", b.offsets().length + " calls", 0); }
    public Described returnFromSequence(ReturnFromSequence b) { return new Described(0x27, "Return", "", 0); }
    public Described select(Select b) { return new Described(0x28, "Select", "", 0); }
    public Described stopIf48K(StopIf48K b) { return new Described(0x2a, "Stop if 48K", "", 0); }
    public Described signalLevel(SignalLevel b) { return new Described(0x2b, "Signal level", b.level() == 0 ? "low" : "high", 0); }
    public Described text(Text b) { return new Described(0x30, "Text", b.text(), 0); }
    public Described message(Message b) { return new Described(0x31, "Message", b.text(), 0); }
    public Described archiveInfo(ArchiveInfo b) { return new Described(0x32, "Archive info", "", 0); }
    public Described hardwareInfo(HardwareInfo b) { return new Described(0x33, "Hardware", b.body().length / 3 + " machines", 0); }
    public Described customInfo(CustomInfo b) { return new Described(0x35, "Custom info", b.id().trim(), 0); }
    public Described glue(Glue b) { return new Described(0x5a, "Glue", "", 0); }
    public Described cswTape(CswTape b) { return new Described(-1, "CSW recording", b.sampleRate() + " Hz", b.data().length); }
    public Described pulseRun(PulseRun b) { return new Described(-1, "Pulses", b.lengths().length + " runs", 0); }
    public Described encodedData(EncodedData b) { return new Described(-1, "Data", b.bits() + " bits", b.data().length); }
    public Described silence(Silence b) { return new Described(-1, "Pause", b.tstates() + " T-states", 0); }
    public Described pzxInfo(PzxInfo b) { return new Described(-1, "PZX header", "", 0); }
  };

  /** A ROM block: a header says what it is and its name; data says how long it is. */
  private static Described data(int id, String type, byte[] data) {
    String details = data.length + " bytes";
    if (data.length == 19 && data[0] == 0x00) {
      String[] kinds = {"Program", "Number array", "Character array", "Bytes"};
      int kind = data[1] & 0xff;
      String name = new String(data, 2, 10, StandardCharsets.ISO_8859_1).trim();
      details = (kind < kinds.length ? kinds[kind] : "Header") + ": " + name;
    }
    return new Described(id, type, details, data.length);
  }
}

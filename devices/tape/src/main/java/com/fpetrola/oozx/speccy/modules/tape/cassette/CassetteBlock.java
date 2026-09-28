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

/**
 * One block of a cassette: what sounds - data, tones, pulses, recordings - and what says how the
 * tape is played - pauses, jumps, loops, calls, stops - and what only says something about it. A
 * closed set, so it is visited: the player makes each one sound, a format writes each one, a
 * window names each one, and none of them is a switch.
 */
public sealed interface CassetteBlock {

  <R> R accept(Visitor<R> visitor);

  /** A block of a TAP: bytes as the ROM saves them, which sound with the ROM's timings and a second after. */
  record TapData(byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.tapData(this); }
  }

  /** 0x10: bytes with the ROM's timings, and a pause of its own after them, in milliseconds. */
  record StandardData(int pause, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.standardData(this); }
  }

  /** 0x11: bytes with timings of their own, in T-states, and only so many bits of the last byte. */
  record TurboData(int pilot, int sync1, int sync2, int zero, int one, int pilotPulses, int usedBits, int pause, byte[] data)
      implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.turboData(this); }
  }

  /** 0x12: a tone, so many pulses of the same length. */
  record PureTone(int length, int pulses) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pureTone(this); }
  }

  /** 0x13: pulses of the lengths given. */
  record PulseSequence(int[] lengths) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pulseSequence(this); }
  }

  /** 0x14: bytes without pilot or sync. */
  record PureData(int zero, int one, int usedBits, int pause, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pureData(this); }
  }

  /** 0x15: the level itself, a sample a bit, so many T-states a sample. */
  record DirectRecording(int tstatesPerSample, int pause, int usedBits, byte[] samples) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.directRecording(this); }
  }

  /** 0x18: a CSW recording inside a TZX: pulse lengths in samples, run-length coded or packed. */
  record CswRecording(int pause, int sampleRate, int compression, int pulses, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.cswRecording(this); }
  }

  /** 0x19: a generalized data block, kept as it came: nothing here plays it yet. */
  record GeneralizedData(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.generalizedData(this); }
  }

  /** 0x20: silence for so many milliseconds; none at all is "stop the tape". */
  record Pause(int milliseconds) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pause(this); }
  }

  /** 0x21 and 0x22: blocks grouped under a name. */
  record GroupStart(String name) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.groupStart(this); }
  }

  record GroupEnd() implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.groupEnd(this); }
  }

  /** 0x23: go so many blocks back or forward. */
  record Jump(int offset) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.jump(this); }
  }

  /** 0x24 and 0x25: the blocks between them, so many times. */
  record LoopStart(int repetitions) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.loopStart(this); }
  }

  record LoopEnd() implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.loopEnd(this); }
  }

  /** 0x26 and 0x27: the blocks at those offsets, one after another, each until a return. */
  record CallSequence(int[] offsets) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.callSequence(this); }
  }

  record ReturnFromSequence() implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.returnFromSequence(this); }
  }

  /** 0x28: a menu of places to start from, kept as it came. */
  record Select(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.select(this); }
  }

  /** 0x2A: stop the tape if the machine is a 48K. */
  record StopIf48K() implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.stopIf48K(this); }
  }

  /** 0x2B: the level the signal is at from here. */
  record SignalLevel(int level) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.signalLevel(this); }
  }

  /** 0x30, 0x31, 0x32, 0x33, 0x35: what only says something, kept as it came. */
  record Text(String text) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.text(this); }
  }

  record Message(int seconds, String text) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.message(this); }
  }

  record ArchiveInfo(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.archiveInfo(this); }
  }

  record HardwareInfo(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.hardwareInfo(this); }
  }

  record CustomInfo(String id, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.customInfo(this); }
  }

  /** 0x5A: another TZX's header, where two were joined. */
  record Glue(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.glue(this); }
  }

  /** A whole CSW file: a recording from the first pulse to the last, at its level; its header kept as it came. */
  record CswTape(int major, int sampleRate, int compression, boolean startsLow, byte[] header, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.cswTape(this); }
  }

  /** PZX: pulses, each so many times, the first starting low; every pulse ends in an edge. */
  record PulseRun(int[] repeats, int[] lengths) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pulseRun(this); }
  }

  /** PZX: so many bits, each a pulse sequence of its own for 0 or for 1, from a level, and a tail. */
  record EncodedData(int bits, boolean startsHigh, int tail, int[] zero, int[] one, byte[] data) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.encodedData(this); }
  }

  /** PZX: silence for so many T-states, at a level. */
  record Silence(int tstates, boolean high) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.silence(this); }
  }

  /** PZX: the header, its version and what it says of the tape, kept as it came. */
  record PzxInfo(byte[] body) implements CassetteBlock {
    public <R> R accept(Visitor<R> v) { return v.pzxInfo(this); }
  }

  /** Who does something with each kind of block. */
  interface Visitor<R> {
    R tapData(TapData b);
    R standardData(StandardData b);
    R turboData(TurboData b);
    R pureTone(PureTone b);
    R pulseSequence(PulseSequence b);
    R pureData(PureData b);
    R directRecording(DirectRecording b);
    R cswRecording(CswRecording b);
    R generalizedData(GeneralizedData b);
    R pause(Pause b);
    R groupStart(GroupStart b);
    R groupEnd(GroupEnd b);
    R jump(Jump b);
    R loopStart(LoopStart b);
    R loopEnd(LoopEnd b);
    R callSequence(CallSequence b);
    R returnFromSequence(ReturnFromSequence b);
    R select(Select b);
    R stopIf48K(StopIf48K b);
    R signalLevel(SignalLevel b);
    R text(Text b);
    R message(Message b);
    R archiveInfo(ArchiveInfo b);
    R hardwareInfo(HardwareInfo b);
    R customInfo(CustomInfo b);
    R glue(Glue b);
    R cswTape(CswTape b);
    R pulseRun(PulseRun b);
    R encodedData(EncodedData b);
    R silence(Silence b);
    R pzxInfo(PzxInfo b);
  }
}

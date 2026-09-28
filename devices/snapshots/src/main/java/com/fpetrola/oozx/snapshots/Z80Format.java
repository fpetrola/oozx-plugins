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

package com.fpetrola.oozx.snapshots;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Pages;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Rule;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.machine.Paging128;
import com.fpetrola.oozx.speccy.machine.PagingPlus3;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.machine.Machine;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import dev.crystal.plugins.api.Answers;

import java.util.List;

import static com.fpetrola.oozx.formats.CoreFields.*;

/**
 * Z80: the header of 30 bytes is the three versions'; the 2 and the 3 add an extended header that
 * says the machine, the paging, the AY and where in the frame it was, and keep the memory in
 * numbered pages. It is read as libspectrum reads it, and written as a version 3.
 */
@Answers("z80")
public final class Z80Format extends DeclaredFormat implements SnapshotFormat {

  static final Fixed HEADER = Fixed.of("header", 30);
  /** The extended header, at the file's offsets as the specification numbers them; each version cuts it where it ends. */
  static final Fixed EXTENDED = Fixed.span("extended", 30, 87);

  /** The eighth bit of R, kept apart in bit 0 of byte 12. */
  static final Field<Cpu, Integer> R_HIGH = Field.of("r7", cpu -> R.get(cpu) >> 7, (cpu, v) -> R.set(cpu, R.get(cpu) & 0x7f | (v & 1) << 7));

  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u8(HEADER, 0, A).u8(HEADER, 1, F).u16(HEADER, 2, BC).u16(HEADER, 4, HL).u16(HEADER, 6, PC)
      .u16(HEADER, 8, SP).u8(HEADER, 10, I)
      .bits(HEADER, 11, 0, 0x7f, R).bits(HEADER, 12, 0, 0x01, R_HIGH)
      .u16(HEADER, 13, DE).u16(HEADER, 15, BC_).u16(HEADER, 17, DE_).u16(HEADER, 19, HL_)
      .u8(HEADER, 21, A_).u8(HEADER, 22, F_).u16(HEADER, 23, IY).u16(HEADER, 25, IX)
      .flag(HEADER, 27, IFF1).flag(HEADER, 28, IFF2)
      .bits(HEADER, 29, 0, 0x03, IM)
      .u16(EXTENDED, 32, PC)
      .assuming(MEMPTR, 0);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().bits(HEADER, 12, 1, 0x07, BORDER);
  static final Layout<Machine> UNIT = Layout.<Machine>of().bit(HEADER, 29, 2, ISSUE_2);
  static final Layout<PagingPlus3> PAGING_PLUS3 = Layout.<PagingPlus3>of().u8(EXTENDED, 86, PORT_1FFD);
  static final Layout<Paging128> PAGING = Layout.<Paging128>of().u8(EXTENDED, 35, PORT_7FFD);
  static final Layout<AyPeripheral> AY = Layout.<AyPeripheral>of()
      .bytes(EXTENDED, 39, 16, AyFields.REGISTERS).u8(EXTENDED, 38, AyFields.SELECTED);
  static final Layout<SpectrumZ80Clock> CLOCK = Layout.<SpectrumZ80Clock>of()
      .assuming(TSTATES, FrameCounter.WITHOUT_COUNTER)
      .encoded(EXTENDED, 55, 3, new FrameCounter());
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Machine.class, UNIT).on(PagingPlus3.class, PAGING_PLUS3).on(Paging128.class, PAGING)
      .on(Cpu.class, CPU).on(Border.class, BORDER_TABLE).on(SpectrumZ80Clock.class, CLOCK)
      .on(SpectrumMemory.class, RAM).on(AyPeripheral.class, AY)
      .quiet(Quiet.PARTS);

  /** Byte 12 at 255 means 1, for compatibility with the oldest writers. */
  static final Rule BYTE_12 = new Rule() {
    public void afterParsing(SnapshotFile file) {
      if (file.u8(HEADER, 12) == 0xff) file.u8(HEADER, 12, 0x01);
    }

    public void beforeAssembling(SnapshotFile file) {
    }
  };

  static final Shape V1_RAW = Shape.of(MachineTypes.SPECTRUM48K, HEADER, Pages.of(5, 2, 0)).with(BYTE_12);
  static final Shape V1_PACKED = Shape.of(MachineTypes.SPECTRUM48K, HEADER, new Z80PackedMemory()).with(BYTE_12);

  @Override
  protected List<String> extensions() {
    return List.of("z80");
  }

  @Override
  protected Bindings bindings() {
    return BINDINGS;
  }

  @Override
  protected Shape shapeOf(Cursor peek) {
    if (peek.u16At(6) != 0) {
      int flags = peek.u8At(12) == 0xff ? 1 : peek.u8At(12);
      return (flags & 0x20) != 0 ? V1_PACKED : V1_RAW;
    }
    Version version = Version.ofLength(peek.u16At(30));
    Hardware hardware = Hardware.read(version, peek.u8At(34));
    boolean modified = (peek.u8At(37) & 0x80) != 0;
    return version.shape(hardware.machine(modified), hardware, modified);
  }

  @Override
  protected Shape shapeFor(SpectrumMachine machine) {
    MachineTypes model = machine.snapshotModel();
    Hardware hardware = Hardware.forWriting(model);
    Version version = machine instanceof PagingPlus3 ? Version.V3_WITH_1FFD : Version.V3;
    return version.shape(model, hardware, model == MachineTypes.SPECTRUM16K);
  }

  @Override
  public String label() {
    return "Z80 snapshot";
  }

  /** The 2 and the 3, and where the extended header of each ends. */
  enum Version {
    V2(23, 2), V3(54, 3), V3_WITH_1FFD(55, 3);

    final int length;
    final int number;

    Version(int length, int number) {
      this.length = length;
      this.number = number;
    }

    static Version ofLength(int length) {
      for (Version version : values()) {
        if (version.length == length) return version;
      }
      throw new Refused("an extended header of " + length + " bytes");
    }

    Shape shape(MachineTypes machine, Hardware hardware, boolean modified) {
      return Shape.of(machine, HEADER, EXTENDED.upTo(32 + length), new Z80Pages())
          .with(new Extended(this, hardware, modified));
    }
  }

  /** What only the writer of a version 2 or 3 puts: the length, PC out of the header, the hardware, and ROM in the first 16K. */
  record Extended(Version version, Hardware hardware, boolean modified) implements Rule {
    public void afterParsing(SnapshotFile file) {
    }

    public void beforeAssembling(SnapshotFile file) {
      file.u16(HEADER, 6, 0);
      file.u16(EXTENDED, 30, version.length);
      file.u8(EXTENDED, 34, version.number == 2 ? hardware.v2 : hardware.v3);
      Bytes extended = file.bytes(EXTENDED);
      extended.or(37, modified ? 0x80 : 0x00);
      if (version.number == 3) {
        extended.u8(61, 0xff).u8(62, 0xff);
      }
    }
  }

  /** Byte 34, as libspectrum reads it: version 2 and 3 number the first rows differently, and agree from 7 on. */
  enum Hardware {
    FORTY_EIGHT(0, 0, MachineTypes.SPECTRUM48K),
    FORTY_EIGHT_IF1(1, 1, MachineTypes.SPECTRUM48K),
    FORTY_EIGHT_SAMRAM(2, 2, MachineTypes.SPECTRUM48K),
    FORTY_EIGHT_MGT(-1, 3, MachineTypes.SPECTRUM48K),
    ONE_TWENTY_EIGHT(3, 4, MachineTypes.SPECTRUM128K),
    ONE_TWENTY_EIGHT_IF1(4, 5, MachineTypes.SPECTRUM128K),
    ONE_TWENTY_EIGHT_MGT(-1, 6, MachineTypes.SPECTRUM128K),
    PLUS_3(7, 7, MachineTypes.SPECTRUMPLUS3),
    PLUS_3_XZX(8, 8, MachineTypes.SPECTRUMPLUS3),
    PENTAGON(9, 9, MachineTypes.SPECTRUM128K),
    PLUS_2(12, 12, MachineTypes.SPECTRUMPLUS2),
    PLUS_2A(13, 13, MachineTypes.SPECTRUMPLUS2A);

    final int v2;
    final int v3;
    final MachineTypes machine;

    Hardware(int v2, int v3, MachineTypes machine) {
      this.v2 = v2;
      this.v3 = v3;
      this.machine = machine;
    }

    static Hardware read(Version version, int code) {
      for (Hardware hardware : values()) {
        if ((version.number == 2 ? hardware.v2 : hardware.v3) == code) return hardware;
      }
      throw new Refused("hardware " + code + " of a version " + version.number + " is not a machine this build has");
    }

    /** Bit 7 of byte 37: the 48 was a 16, the 128 a +2, the +3 a +2A. */
    MachineTypes machine(boolean modified) {
      if (!modified) return machine;
      return switch (machine) {
        case SPECTRUM48K -> MachineTypes.SPECTRUM16K;
        case SPECTRUM128K -> MachineTypes.SPECTRUMPLUS2;
        case SPECTRUMPLUS3 -> MachineTypes.SPECTRUMPLUS2A;
        default -> machine;
      };
    }

    static Hardware forWriting(MachineTypes machine) {
      return switch (machine) {
        case SPECTRUM16K, SPECTRUM48K -> FORTY_EIGHT;
        case SPECTRUM128K -> ONE_TWENTY_EIGHT;
        case SPECTRUMPLUS2 -> PLUS_2;
        case SPECTRUMPLUS2A -> PLUS_2A;
        case SPECTRUMPLUS3 -> PLUS_3;
      };
    }
  }

  /** Bytes 55 to 57 of a version 3: which quarter of the frame was running, and how much of it was left. */
  static final class FrameCounter implements Encoding<SpectrumZ80Clock> {
    /** What a version 1 or 2, which has no counter, is given: 224 short of a 48K frame, as libspectrum does. */
    static final int WITHOUT_COUNTER = 69664;

    public void read(Bytes bytes, SpectrumZ80Clock clock, SnapshotFile file) {
      int quarter = file.shape().machine().tstatesFrame / 4;
      int tstates = ((bytes.u8(57) + 1) % 4 + 1) * quarter - (bytes.u16(55) + 1);
      TSTATES.set(clock, tstates);
    }

    public void write(SpectrumZ80Clock clock, Bytes bytes, SnapshotFile file) {
      int quarter = file.shape().machine().tstatesFrame / 4;
      int tstates = TSTATES.get(clock);
      bytes.u16(55, quarter - tstates % quarter - 1).u8(57, (tstates / quarter + 3) % 4);
    }

    public List<Object> fields() {
      return List.of(TSTATES);
    }
  }
}

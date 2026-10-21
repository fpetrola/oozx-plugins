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
import com.fpetrola.oozx.formats.Codes;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Rule;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
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
import java.util.stream.Stream;

import static com.fpetrola.oozx.formats.CoreFields.*;

/**
 * SZX, from Spectaculator: a header of 8 bytes that says the machine, and blocks, each an id and
 * its bytes. Each part the machine has is in a block of its own: the processor in Z80R, the ULA's
 * ports in SPCR, issue 2 in KEYB, the RAM in one RAMP per bank. The blocks of what is plugged in,
 * the AY's or the ULAplus', are the mappings'.
 */
@Answers("szx")
public final class SzxFormat extends DeclaredFormat implements SnapshotFormat {

  static final Fixed HEADER = Fixed.of("header", 8);
  static final Fixed Z80R = Fixed.of("Z80R", 37);
  static final Fixed SPCR = Fixed.of("SPCR", 8);
  static final Fixed KEYB = Fixed.of("KEYB", 5);

  static final Codes<MachineTypes> MACHINES = Codes.<MachineTypes>of("machine")
      .is(0, MachineTypes.SPECTRUM16K).is(1, MachineTypes.SPECTRUM48K).is(2, MachineTypes.SPECTRUM128K)
      .is(3, MachineTypes.SPECTRUMPLUS2).is(4, MachineTypes.SPECTRUMPLUS2A).is(5, MachineTypes.SPECTRUMPLUS3)
      .is(6, MachineTypes.SPECTRUMPLUS3E).is(7, MachineTypes.PENTAGON128).is(8, MachineTypes.TC2048)
      .is(9, MachineTypes.TC2068).is(10, MachineTypes.SCORPION).is(11, MachineTypes.SPECTRUMSE)
      .is(12, MachineTypes.TS2068).is(13, MachineTypes.PENTAGON512).is(14, MachineTypes.PENTAGON1024)
      .is(15, MachineTypes.SPECTRUM48KNTSC).is(16, MachineTypes.SPECTRUM128KE);

  /** Without a Z80R the processor is left as libspectrum leaves it (decision 8); a Z80R says the rest over it. */
  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .assuming(AF, 0).assuming(BC, 0).assuming(DE, 0).assuming(HL, 0)
      .assuming(AF_, 0).assuming(BC_, 0).assuming(DE_, 0).assuming(HL_, 0)
      .assuming(IX, 0).assuming(IY, 0).assuming(SP, 0).assuming(PC, 0).assuming(I, 0).assuming(R, 0)
      .assuming(IFF1, true).assuming(IFF2, true).assuming(IM, 1).assuming(MEMPTR, 0)
      .u16(Z80R, 0, AF).u16(Z80R, 2, BC).u16(Z80R, 4, DE).u16(Z80R, 6, HL)
      .u16(Z80R, 8, AF_).u16(Z80R, 10, BC_).u16(Z80R, 12, DE_).u16(Z80R, 14, HL_)
      .u16(Z80R, 16, IX).u16(Z80R, 18, IY).u16(Z80R, 20, SP).u16(Z80R, 22, PC)
      .u8(Z80R, 24, I).u8(Z80R, 25, R).flag(Z80R, 26, IFF1).flag(Z80R, 27, IFF2).u8(Z80R, 28, IM)
      .bit(Z80R, 34, 0, EI_PENDING).bit(Z80R, 34, 1, HALTED)
      .u16(Z80R, 35, MEMPTR);
  static final Layout<SpectrumZ80Clock> CLOCK = Layout.<SpectrumZ80Clock>of().assuming(TSTATES, 69664).u32(Z80R, 29, TSTATES);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().bits(SPCR, 0, 0, 0x07, BORDER);
  static final Layout<Paging128> PAGING = Layout.<Paging128>of().u8(SPCR, 1, PORT_7FFD);
  static final Layout<PagingPlus3> PAGING_PLUS3 = Layout.<PagingPlus3>of().u8(SPCR, 2, PORT_1FFD);
  static final Layout<Machine> UNIT = Layout.<Machine>of().bit(KEYB, 0, 0, ISSUE_2);
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Machine.class, UNIT).on(PagingPlus3.class, PAGING_PLUS3).on(Paging128.class, PAGING)
      .on(Cpu.class, CPU).on(Border.class, BORDER_TABLE).on(SpectrumZ80Clock.class, CLOCK)
      .on(SpectrumMemory.class, RAM)
      .quiet(Quiet.PARTS);

  @Override
  protected List<String> extensions() {
    return List.of("szx");
  }

  @Override
  protected Bindings bindings() {
    return BINDINGS;
  }

  @Override
  protected Shape shapeOf(Cursor peek) {
    if (peek.u8At(0) != 'Z' || peek.u8At(1) != 'X' || peek.u8At(2) != 'S' || peek.u8At(3) != 'T') {
      throw new Refused("not an SZX: it does not start with ZXST");
    }
    return shapeOf(MACHINES.decode(peek.u8At(6)));
  }

  @Override
  protected Shape shapeFor(SpectrumMachine machine) {
    return shapeOf(machine.snapshotModel());
  }

  private Shape shapeOf(MachineTypes machine) {
    List<Fixed> blocks = Stream.concat(Stream.of(Z80R, SPCR, KEYB), mappings().stream().flatMap(mapping -> mapping.blocks().stream())).toList();
    return Shape.of(machine, HEADER, new SzxBlocks(blocks)).with(new Signed(machine));
  }

  @Override
  public String label() {
    return "SZX snapshot";
  }

  /** The header: ZXST, version 1.4, the machine, and no flags. */
  record Signed(MachineTypes machine) implements Rule {
    public void afterParsing(SnapshotFile file) {
    }

    public void beforeAssembling(SnapshotFile file) {
      Bytes header = file.bytes(HEADER);
      header.put(0, new byte[]{'Z', 'X', 'S', 'T'}).u8(4, 1).u8(5, 4).u8(6, MACHINES.encode(machine)).u8(7, 0);
    }
  }
}

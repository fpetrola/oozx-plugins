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
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.Codes;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Rule;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.devices.ulaplus.UlaPlusPeripheral;
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
 * SZX, from Spectaculator: a header of 8 bytes that says the machine, and blocks, each an id and
 * its bytes. Each part the machine has is in a block of its own: the processor in Z80R, the ULA's
 * ports in SPCR, issue 2 in KEYB, the AY in AY, the ULAplus in PLTT, the RAM in one RAMP per bank.
 */
@Answers("szx")
public final class SzxFormat extends DeclaredFormat implements SnapshotFormat {

  static final Fixed HEADER = Fixed.of("header", 8);
  static final Fixed Z80R = Fixed.of("Z80R", 37);
  static final Fixed SPCR = Fixed.of("SPCR", 8);
  static final Fixed KEYB = Fixed.of("KEYB", 5);
  static final Fixed AY = Fixed.of("AY\0\0", 18);
  static final Fixed PLTT = Fixed.of("PLTT", 67);

  static final Codes<MachineTypes> MACHINES = Codes.<MachineTypes>of("machine")
      .is(0, MachineTypes.SPECTRUM16K).is(1, MachineTypes.SPECTRUM48K).is(2, MachineTypes.SPECTRUM128K)
      .is(3, MachineTypes.SPECTRUMPLUS2).is(4, MachineTypes.SPECTRUMPLUS2A).is(5, MachineTypes.SPECTRUMPLUS3);

  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u16(Z80R, 0, AF).u16(Z80R, 2, BC).u16(Z80R, 4, DE).u16(Z80R, 6, HL)
      .u16(Z80R, 8, AF_).u16(Z80R, 10, BC_).u16(Z80R, 12, DE_).u16(Z80R, 14, HL_)
      .u16(Z80R, 16, IX).u16(Z80R, 18, IY).u16(Z80R, 20, SP).u16(Z80R, 22, PC)
      .u8(Z80R, 24, I).u8(Z80R, 25, R).flag(Z80R, 26, IFF1).flag(Z80R, 27, IFF2).u8(Z80R, 28, IM)
      .bit(Z80R, 34, 0, EI_PENDING).bit(Z80R, 34, 1, HALTED)
      .u16(Z80R, 35, MEMPTR);
  static final Layout<SpectrumZ80Clock> CLOCK = Layout.<SpectrumZ80Clock>of().u32(Z80R, 29, TSTATES);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().bits(SPCR, 0, 0, 0x07, BORDER);
  static final Layout<Paging128> PAGING = Layout.<Paging128>of().u8(SPCR, 1, PORT_7FFD);
  static final Layout<PagingPlus3> PAGING_PLUS3 = Layout.<PagingPlus3>of().u8(SPCR, 2, PORT_1FFD);
  static final Layout<Machine> UNIT = Layout.<Machine>of().bit(KEYB, 0, 0, ISSUE_2);
  static final Layout<AyPeripheral> AY_TABLE = Layout.<AyPeripheral>of()
      .bytes(AY, 2, 16, AyFields.REGISTERS).u8(AY, 1, AyFields.SELECTED);
  static final Layout<UlaPlusPeripheral> ULAPLUS = Layout.<UlaPlusPeripheral>of().encoded(PLTT, 0, 66, new Palette());
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Machine.class, UNIT).on(PagingPlus3.class, PAGING_PLUS3).on(Paging128.class, PAGING)
      .on(Cpu.class, CPU).on(Border.class, BORDER_TABLE).on(SpectrumZ80Clock.class, CLOCK)
      .on(SpectrumMemory.class, RAM).on(AyPeripheral.class, AY_TABLE).on(UlaPlusPeripheral.class, ULAPLUS)
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

  private static Shape shapeOf(MachineTypes machine) {
    return Shape.of(machine, HEADER, new SzxBlocks()).with(new Signed(machine));
  }

  /** The ULAplus is in the machine if the file has its block, and not if it does not: what the old loader did too. */
  @Override
  protected void plug(SnapshotFile file, Speccy speccy) {
    if (speccy.peripheralRegistry.find(UlaPlusPeripheral.class) instanceof UlaPlusPeripheral chip) {
      chip.fitted(file.has(PLTT));
      speccy.peripheralRegistry.update();
    }
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

  /** PLTT: whether the palette is on, the register last named, and the sixty-four colours as written. */
  static final class Palette implements Encoding<UlaPlusPeripheral> {
    public void read(Bytes bytes, UlaPlusPeripheral chip, SnapshotFile file) {
      int[] colours = new int[64];
      for (int colour = 0; colour < 64; colour++) colours[colour] = bytes.u8(2 + colour);
      chip.asItWas(colours, bytes.u8(0) == 1);
    }

    public void write(UlaPlusPeripheral chip, Bytes bytes, SnapshotFile file) {
      bytes.u8(0, chip.inUse() ? 1 : 0).u8(1, chip.named());
      for (int colour = 0; colour < 64; colour++) bytes.u8(2 + colour, chip.register(colour));
    }

    public List<Object> fields() {
      return List.of();
    }
  }
}

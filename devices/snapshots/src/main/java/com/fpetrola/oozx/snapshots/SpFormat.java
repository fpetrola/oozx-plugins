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
import com.fpetrola.oozx.formats.Codes;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Pages;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Rule;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import dev.crystal.plugins.api.Answers;

import java.util.List;

import static com.fpetrola.oozx.formats.CoreFields.*;

/**
 * SP, from the VGASPEC emulator: a header of 38 bytes that says the processor and the border, and
 * the RAM from 0x4000, 16K or 48K of it. Only read.
 */
@Answers("sp")
public final class SpFormat extends DeclaredFormat implements SnapshotFormat {

  static final Fixed HEADER = Fixed.of("header", 38);

  /** The status byte: IFF1 in bit 0, IFF2 in bit 2, and the interrupt mode in bits 1 and 3. */
  static final Codes<Integer> MODES = Codes.<Integer>of("interrupt mode").is(0x00, 1).is(0x02, 2).mask(0x08, 0);

  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u16(HEADER, 6, BC).u16(HEADER, 8, DE).u16(HEADER, 10, HL).u16(HEADER, 12, AF)
      .u16(HEADER, 14, IX).u16(HEADER, 16, IY)
      .u16(HEADER, 18, BC_).u16(HEADER, 20, DE_).u16(HEADER, 22, HL_).u16(HEADER, 24, AF_)
      .u8(HEADER, 26, R).u8(HEADER, 27, I).u16(HEADER, 28, SP).u16(HEADER, 30, PC)
      .bit(HEADER, 36, 0, IFF1).bit(HEADER, 36, 2, IFF2)
      .code(HEADER, 36, 0, 0x0a, IM, MODES)
      .assuming(MEMPTR, 0);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().u8(HEADER, 34, BORDER);
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Cpu.class, CPU).on(Border.class, BORDER_TABLE).on(SpectrumMemory.class, RAM)
      .quiet(Quiet.PARTS);

  /** "SP", the length of the RAM, and where it starts: always 0x4000. */
  static final Rule SIGNED = new Rule() {
    public void afterParsing(SnapshotFile file) {
      if (file.u8(HEADER, 0) != 'S' || file.u8(HEADER, 1) != 'P') throw new Refused("not an SP: it does not start with SP");
      int length = file.u16(HEADER, 2);
      if (length != 16384 && length != 49152 || file.u16(HEADER, 4) != 0x4000) {
        throw new Refused("an SP of " + length + " bytes from " + Integer.toHexString(file.u16(HEADER, 4)));
      }
    }

    public void beforeAssembling(SnapshotFile file) {
    }
  };

  static final Shape SIXTEEN = Shape.of(MachineTypes.SPECTRUM16K, HEADER, Pages.of(5)).with(SIGNED);
  static final Shape FORTY_EIGHT = Shape.of(MachineTypes.SPECTRUM48K, HEADER, Pages.of(5, 2, 0)).with(SIGNED);

  @Override
  protected List<String> extensions() {
    return List.of("sp");
  }

  @Override
  protected Bindings bindings() {
    return BINDINGS;
  }

  @Override
  protected boolean writesFiles() {
    return false;
  }

  @Override
  protected Shape shapeOf(Cursor peek) {
    return switch (peek.length()) {
      case 38 + 16384 -> SIXTEEN;
      case 38 + 49152 -> FORTY_EIGHT;
      default -> throw new Refused("an SP of " + peek.length() + " bytes");
    };
  }

  @Override
  protected Shape shapeFor(SpectrumMachine machine) {
    return FORTY_EIGHT;
  }

  @Override
  public String label() {
    return "SP snapshot";
  }
}

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
import com.fpetrola.oozx.speccy.machine.Paging128;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import dev.crystal.plugins.api.Answers;
import dev.crystal.plugins.api.Needs;

import java.util.List;

import static com.fpetrola.oozx.formats.CoreFields.*;

/**
 * SNA: the header of 27 bytes and the 48K of RAM; a 128K adds the PC, the 7ffd and the banks
 * missing. A 48K has nowhere for the PC, and keeps it on the stack.
 */
@Answers("sna")
@Needs({"device-spectrum128"})
public final class SnaFormat extends DeclaredFormat implements SnapshotFormat {

  static final Fixed HEADER = Fixed.of("header", 27);
  /** 128K: the PC, the 7ffd, and a byte for TR-DOS that is written 0 and not looked at. */
  static final Fixed TAIL = Fixed.of("tail", 4);
  /** 48K: the PC the rule takes off the stack, or puts on it. */
  static final Fixed STACKED = Fixed.virtual("stacked", 2);

  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u8(HEADER, 0, I)
      .u16(HEADER, 1, HL_).u16(HEADER, 3, DE_).u16(HEADER, 5, BC_).u16(HEADER, 7, AF_)
      .u16(HEADER, 9, HL).u16(HEADER, 11, DE).u16(HEADER, 13, BC).u16(HEADER, 15, IY).u16(HEADER, 17, IX)
      .bit(HEADER, 19, 2, IFF2).alsoSets(IFF1)
      .u8(HEADER, 20, R).u16(HEADER, 21, AF).u16(HEADER, 23, SP)
      .bits(HEADER, 25, 0, 0x03, IM)
      .u16(TAIL, 0, PC).u16(STACKED, 0, PC)
      .assuming(MEMPTR, 0);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().bits(HEADER, 26, 0, 0x07, BORDER);
  static final Layout<Paging128> PAGING = Layout.<Paging128>of().u8(TAIL, 2, PORT_7FFD);
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Cpu.class, CPU).on(Border.class, BORDER_TABLE).on(Paging128.class, PAGING).on(SpectrumMemory.class, RAM)
      .quiet(Quiet.PARTS);

  static final Shape FORTY_EIGHT = Shape.of(MachineTypes.SPECTRUM48K, HEADER, STACKED, Pages.of(5, 2, 0)).with(new PcOnTheStack());
  static final Shape ONE_TWENTY_EIGHT = Shape.of(MachineTypes.SPECTRUM128K,
      HEADER, Pages.of(5, 2), Pages.bankAtTop(TAIL, 2), TAIL, Pages.remaining());

  @Override
  protected List<String> extensions() {
    return List.of("sna");
  }

  @Override
  protected Bindings bindings() {
    return BINDINGS;
  }

  @Override
  protected Shape shapeOf(Cursor peek) {
    return switch (peek.length()) {
      case 49179 -> FORTY_EIGHT;
      case 131103, 147487 -> ONE_TWENTY_EIGHT;
      default -> throw new Refused("an SNA of " + peek.length() + " bytes");
    };
  }

  /** A machine that pages goes as a 128K, and one that does not as a 48K: what the SNA can say. */
  @Override
  protected Shape shapeFor(SpectrumMachine machine) {
    return machine.pagesThrough7ffd() ? ONE_TWENTY_EIGHT : FORTY_EIGHT;
  }

  @Override
  public String label() {
    return "SNA snapshot";
  }

  /** A 48K SNA has nowhere for the PC: it goes on the stack, as if an interrupt had pushed it. */
  static final class PcOnTheStack implements Rule {
    public void afterParsing(SnapshotFile file) {
      int sp = file.u16(HEADER, 23);
      if (sp < 0x4000 || sp == 0xffff) {
        throw new Refused(String.format("the SP, 0x%04x, is not where a PC can be taken from", sp));
      }
      file.u16(STACKED, 0, file.ram().word(sp));
      file.u16(HEADER, 23, sp + 2 & 0xffff);
    }

    public void beforeAssembling(SnapshotFile file) {
      int sp = file.u16(HEADER, 23);
      if (sp < 0x4002) {
        throw new Refused(String.format("the SP, 0x%04x, is too low to push the PC on", sp));
      }
      file.ram().word(sp - 2, file.u16(STACKED, 0));
      file.u16(HEADER, 23, sp - 2);
    }
  }
}

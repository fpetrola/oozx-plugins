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

package com.fpetrola.oozx.mappings;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.machine.DockCartridge;
import com.fpetrola.oozx.speccy.machine.Tc2068;
import com.fpetrola.oozx.speccy.modules.memory.Ram;
import com.fpetrola.oozx.speccy.modules.memory.Storage;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * A Timex's dock in an SZX: a DOCK for each page of eight K its cartridge brings, to the dock or to the EXROM behind
 * it, saying whether it is RAM. SZX keeps no file for a cartridge, so every page goes inside, ROM ones too, as
 * libspectrum writes them. A file with DOCK puts a cartridge of those pages in the dock, as the dock's window does.
 */
@OnlyWith("device-timex")
public final class DockMapping implements Mapping {

  static final Fixed EXROM = Fixed.each("DOCK", 3, 2).only(0, 0x04, 0x00);
  static final Fixed DOCK = Fixed.each("DOCK", 3, 2).only(0, 0x04, 0x04);
  private static final int RAM = 0x02;

  static final Layout<Tc2068> TABLE = Layout.<Tc2068>of()
      .each(EXROM, timex -> 8, 0, (timex, page) -> pageOf(timex, DockCartridge.EXROM, page), page(EXROM))
      .each(DOCK, timex -> 8, 0, (timex, page) -> pageOf(timex, DockCartridge.DOCK, page), page(DOCK));

  /** A page as DOCK keeps it: whether it is RAM, and its eight K. */
  private static Layout<Storage> page(Fixed block) {
    return Layout.<Storage>of()
        .bit(block, 0, 1, Field.of("dock.ram", page -> page instanceof Ram, (page, ram) -> { }))
        .rest(block, 0, 0, 0x2000, Field.of("dock.page", page -> page.bytes, Storage::fill))
        .writtenWhen(page -> page != null);
  }

  private static Storage pageOf(Tc2068 timex, int bank, int page) {
    return timex.cartridge() == null ? null : (Storage) timex.cartridge().bank(bank)[page];
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(Tc2068.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(EXROM, DOCK);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (!(speccy.machine.current instanceof Tc2068 timex) || file.each(EXROM).isEmpty() && file.each(DOCK).isEmpty()) return;
    DockCartridge cartridge = new DockCartridge("From a snapshot");
    file.each(EXROM).forEach((page, bytes) -> cartridge.page(DockCartridge.EXROM, page, (bytes.u8(0) & RAM) != 0));
    file.each(DOCK).forEach((page, bytes) -> cartridge.page(DockCartridge.DOCK, page, (bytes.u8(0) & RAM) != 0));
    timex.insert(cartridge);
    speccy.machine.reset(false);
  }
}

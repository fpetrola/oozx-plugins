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
import com.fpetrola.oozx.formats.Codes;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.disk.Disk;
import com.fpetrola.oozx.speccy.devices.disk.Fdd;
import com.fpetrola.oozx.speccy.devices.plusd.MgtDiskInterface;
import com.fpetrola.oozx.speccy.devices.plusd.PlusDPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The +D in an SZX: PLSD says whether its ROM is paged in, which way the head was last stepping, its control register,
 * the four registers of its controller, and carries its RAM, its kept length before it, and a PDSK for each drive with
 * a disk in it, as the Beta's. Its ROM is GDOS, not written. A file with PLSD plugs it in.
 */
@OnlyWith({"device-plusd", "device-disk"})
public final class PlusDMapping implements Mapping {

  static final Fixed PLSD = Fixed.of("PLSD", 19);
  static final Fixed PDSK = Fixed.each("PDSK", 7, 4);
  static final Codes<Disk.Type> KINDS = Codes.<Disk.Type>of("+D disk type").is(0, Disk.Type.MGT).is(1, Disk.Type.IMG).otherwise(Disk.Type.MGT);

  static final Layout<PlusDPeripheral> TABLE = Layout.<PlusDPeripheral>of()
      .bit(PLSD, 0, 2, Field.of("plusd.seekingLower", plusd -> plusd.fdc().direction == Fdd.Dir.OUT,
          (plusd, lower) -> plusd.fdc().direction = lower ? Fdd.Dir.OUT : Fdd.Dir.IN))
      .u32(PLSD, 8, Field.of("plusd.romLength", plusd -> 0, (plusd, length) -> { }))
      .u8(PLSD, 14, Field.of("plusd.drives", plusd -> MgtDiskInterface.DRIVES, (plusd, drives) -> { }))
      .u8(PLSD, 15, Field.of("plusd.track", plusd -> plusd.fdc().trackRegister, (plusd, track) -> plusd.fdc().trackRegister = track))
      .u8(PLSD, 16, Field.of("plusd.sector", plusd -> plusd.fdc().sectorRegister, (plusd, sector) -> plusd.fdc().sectorRegister = sector))
      .u8(PLSD, 17, Field.of("plusd.data", plusd -> plusd.fdc().dataRegister, (plusd, data) -> plusd.fdc().dataRegister = data))
      .u8(PLSD, 18, Field.of("plusd.status", plusd -> plusd.fdc().statusRegister, (plusd, status) -> plusd.fdc().statusRegister = status))
      .u8(PLSD, 13, Field.of("plusd.control", PlusDPeripheral::controlRegister, PlusDPeripheral::control))
      .bit(PLSD, 0, 0, Field.of("plusd.paged", PlusDPeripheral::isPaged, PlusDPeripheral::pagedIn))
      .rest(PLSD, 0, 1, 4, MgtDiskInterface.RAM_SIZE, Field.of("plusd.ram", PlusDPeripheral::ram, PlusDPeripheral::ram))
      .each(PDSK, plusd -> MgtDiskInterface.DRIVES, 0, PlusDPeripheral::drive, Beta128Mapping.drive(PDSK, KINDS));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(PlusDPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(PLSD, PDSK);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(PLSD) && speccy.peripheralRegistry.find(PlusDPeripheral.class) instanceof PlusDPeripheral plusd) plusd.plugIn(true);
  }
}

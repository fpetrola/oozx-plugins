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
import com.fpetrola.oozx.formats.Layout.Medium;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.interface1.Interface1Peripheral;
import com.fpetrola.oozx.speccy.devices.interface1.Microdrive;
import dev.crystal.plugins.api.OnlyWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The Interface 1 in an SZX, as Fuse keeps it: IF1 says it is on, whether its shadow ROM is paged in, and that it has
 * its eight microdrives, and an MDRV for each with a cartridge in it: by its file, or inside where the machine wrote
 * on it or it has none, whether its motor is running and where its head is. Its own ROM is not written, as Fuse
 * does not unless a snapshot brought it. A file with an IF1 that says it is on plugs it in.
 */
@OnlyWith("device-interface1")
public final class Interface1Mapping implements Mapping {

  static final Fixed IF1 = Fixed.of("IF1\0", 40);
  static final Fixed MDRV = Fixed.each("MDRV", 12, 2);

  static final Layout<Microdrive> DRIVE = Layout.<Microdrive>of()
      .medium(MDRV, 0, 1, 0, 8, -1, -1, Field.of("mdrv.cartridge", Interface1Mapping::keptAs, Interface1Mapping::put))
      .flag(MDRV, 3, Field.of("mdrv.running", drive -> drive.motorOn, (drive, running) -> drive.motorOn = running))
      .u16(MDRV, 4, Field.of("mdrv.head", Microdrive::head, Microdrive::headAt))
      .writtenWhen(drive -> drive.inserted);

  static final Layout<Interface1Peripheral> TABLE = Layout.<Interface1Peripheral>of()
      .bit(IF1, 0, 0, Field.of("interface1.on", interface1 -> true, (interface1, on) -> { }))
      .bit(IF1, 0, 2, Field.of("interface1.paged", Interface1Peripheral::isPaged, Interface1Peripheral::pagedIn))
      .u8(IF1, 2, Field.of("interface1.drives", interface1 -> Interface1Peripheral.DRIVES, (interface1, drives) -> { }))
      .each(MDRV, interface1 -> Interface1Peripheral.DRIVES, 1, Interface1Peripheral::drive, DRIVE);

  private static Medium keptAs(Microdrive drive) {
    return !drive.modified && drive.filename != null ? Medium.inFile(drive.filename, 0) : Medium.inside(drive.image(), 0);
  }

  /** The cartridge the file says, in place of whatever the drive had; one whose file is not there any more leaves it empty. */
  private static void put(Microdrive drive, Medium medium) {
    drive.eject();
    try {
      if (medium.isInside()) drive.insert("the cartridge inside the file", medium.image());
      else drive.insert(new File(medium.file()));
    } catch (IOException leftEmpty) {
    }
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(Interface1Peripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(IF1, MDRV);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.says(IF1, 0, 0x01)
        && speccy.peripheralRegistry.find(Interface1Peripheral.class) instanceof Interface1Peripheral interface1) interface1.plugIn(true);
  }
}

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
import com.fpetrola.oozx.formats.Layout.Medium;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.disk.Beta128Peripheral;
import com.fpetrola.oozx.speccy.devices.disk.Disk;
import com.fpetrola.oozx.speccy.devices.disk.Fdd;
import dev.crystal.plugins.api.OnlyWith;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * The Beta 128 in an SZX, the Pentagon's own or one plugged into a 48K or a 128: B128 says whether TR-DOS is paged in,
 * whether a 48K boots it, which way the head was last stepping, its system register and the four registers of its
 * controller, and a BDSK for each drive with a disk in it. Its own ROM is not written. The 48K boot is the person's
 * setting, and a file does not change it. A file with B128 plugs in the one that goes on the machine, where it is
 * not built in.
 */
@OnlyWith("device-disk")
public final class Beta128Mapping implements Mapping {

  static final Fixed B128 = Fixed.of("B128", 10);
  static final Fixed BDSK = Fixed.each("BDSK", 7, 4);
  static final Codes<Disk.Type> KINDS = Codes.<Disk.Type>of("Beta 128 disk type")
      .is(0, Disk.Type.TRD).is(1, Disk.Type.SCL).is(2, Disk.Type.FDI).is(3, Disk.Type.UDI).otherwise(Disk.Type.TRD);

  static final Layout<Beta128Peripheral> TABLE = Layout.<Beta128Peripheral>of()
      .bit(B128, 0, 0, Field.of("beta128.connected", beta -> true, (beta, connected) -> { }))
      .bit(B128, 0, 2, Field.of("beta128.paged", Beta128Peripheral::isPaged, Beta128Peripheral::pagedIn))
      .bit(B128, 0, 3, Field.of("beta128.bootOn48k", beta -> beta.trdos.bootOn48k, (beta, boots) -> { }))
      .bit(B128, 0, 4, Field.of("beta128.seekingLower", beta -> beta.fdc().direction == Fdd.Dir.OUT,
          (beta, lower) -> beta.fdc().direction = lower ? Fdd.Dir.OUT : Fdd.Dir.IN))
      .u8(B128, 4, Field.of("beta128.drives", beta -> Beta128Peripheral.DRIVES, (beta, drives) -> { }))
      .u8(B128, 5, Field.of("beta128.system", Beta128Peripheral::systemRegister, Beta128Peripheral::system))
      .u8(B128, 6, Field.of("beta128.track", beta -> beta.fdc().trackRegister, (beta, track) -> beta.fdc().trackRegister = track))
      .u8(B128, 7, Field.of("beta128.sector", beta -> beta.fdc().sectorRegister, (beta, sector) -> beta.fdc().sectorRegister = sector))
      .u8(B128, 8, Field.of("beta128.data", beta -> beta.fdc().dataRegister, (beta, data) -> beta.fdc().dataRegister = data))
      .u8(B128, 9, Field.of("beta128.status", beta -> beta.fdc().statusRegister, (beta, status) -> beta.fdc().statusRegister = status))
      .each(BDSK, beta -> Beta128Peripheral.DRIVES, 0, Beta128Peripheral::drive, drive(BDSK, KINDS));

  /**
   * A drive as BDSK keeps it, and PDSK and ODSK for the +D and the Opus, which are the same block: the disk in it by
   * its file, or inside where the machine wrote on it or it has no file, the kind of image it is, whether it is
   * write-protected and the cylinder the head is over. A disk no image can hold yet, one not formatted, is left out,
   * and the drive comes back empty.
   */
  static Layout<Fdd> drive(Fixed block, Codes<Disk.Type> kinds) {
    String name = block.toString().toLowerCase(Locale.ROOT);
    return Layout.<Fdd>of()
        .medium(block, 0, 0, 1, -1, -1, 6, Field.of(name + ".disk", drive -> keptAs(drive.disk, kinds), (drive, medium) -> put(drive, medium, kinds)))
        .bit(block, 0, 2, Field.of(name + ".writeProtected", drive -> drive.wrprot, Fdd::writeProtect))
        .u8(block, 5, Field.of(name + ".cylinder", Fdd::cylinder, Fdd::headAt))
        .writtenWhen(drive -> drive.loaded);
  }

  private static Medium keptAs(Disk disk, Codes<Disk.Type> kinds) {
    if (!disk.dirty && disk.filename != null) return Medium.inFile(disk.filename, kinds.encode(disk.type));
    try {
      return Medium.inside(disk.toImage(), kinds.encode(disk.type));
    } catch (IOException noImageHoldsIt) {
      return null;
    }
  }

  /** The disk the file says, in place of whatever the drive had; one whose file is not there any more leaves it empty. */
  private static void put(Fdd drive, Medium medium, Codes<Disk.Type> kinds) {
    drive.eject();
    try {
      Disk disk = medium.isInside()
          ? Disk.openBuffer("disk." + kinds.decode(medium.kind()).name().toLowerCase(Locale.ROOT), medium.image())
          : Disk.open(new File(medium.file()));
      if (medium.isInside()) {
        disk.filename = null;
        disk.dirty = true;
      }
      drive.insert(disk, false);
    } catch (IOException leftEmpty) {
    }
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(Beta128Peripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(B128, BDSK);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (!file.has(B128) || speccy.machine.current.hasOnBoard(Beta128Peripheral.class)) return;
    speccy.peripheralRegistry.allThatAre(Beta128Peripheral.class).stream()
        .filter(beta -> beta.fitsOn(speccy.machine.current)).forEach(beta -> beta.plugIn(true));
  }
}

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
import com.fpetrola.oozx.speccy.devices.disk.Beta128Peripheral;
import com.fpetrola.oozx.speccy.devices.disk.Fdd;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The Beta 128 in an SZX, the Pentagon's own or one plugged into a 48K or a 128: B128 says whether TR-DOS is paged in,
 * whether a 48K boots it, which way the head was last stepping, its system register and the four registers of its
 * controller. Its own ROM is not written, nor are the disks, which are media. The 48K boot is the person's setting,
 * and a file does not change it. A file with B128 plugs in the one that goes on the machine, where it is not built in.
 */
@OnlyWith("device-disk")
public final class Beta128Mapping implements Mapping {

  static final Fixed B128 = Fixed.of("B128", 10);

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
      .u8(B128, 9, Field.of("beta128.status", beta -> beta.fdc().statusRegister, (beta, status) -> beta.fdc().statusRegister = status));

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
    return List.of(B128);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (!file.has(B128) || speccy.machine.current.hasOnBoard(Beta128Peripheral.class)) return;
    speccy.peripheralRegistry.allThatAre(Beta128Peripheral.class).stream()
        .filter(beta -> beta.fitsOn(speccy.machine.current)).forEach(beta -> beta.plugIn(true));
  }
}

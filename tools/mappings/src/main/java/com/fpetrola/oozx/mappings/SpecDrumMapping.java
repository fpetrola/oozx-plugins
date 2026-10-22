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
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/** The SpecDrum in an SZX: DRUM, the byte last written to it, 128 being silence. A file with DRUM plugs it in. */
@OnlyWith("device-specdrum")
public final class SpecDrumMapping implements Mapping {

  static final Fixed DRUM = Fixed.of("DRUM", 1);

  static final Layout<SpecDrumPeripheral> TABLE = Layout.<SpecDrumPeripheral>of()
      .u8(DRUM, 0, Field.of("specdrum.written", SpecDrumPeripheral::written, SpecDrumPeripheral::written));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(SpecDrumPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(DRUM);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(DRUM) && speccy.peripheralRegistry.find(SpecDrumPeripheral.class) instanceof SpecDrumPeripheral drum) drum.plugIn(true);
  }
}

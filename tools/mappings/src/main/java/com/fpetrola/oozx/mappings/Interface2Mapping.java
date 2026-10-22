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
import com.fpetrola.oozx.speccy.devices.interface2.Cartridge;
import com.fpetrola.oozx.speccy.devices.interface2.Interface2Peripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The Interface 2 in an SZX: the cartridge in its slot, in IF2R, always packed. SZX has no name for it. A file
 * with IF2R plugs it in, with the cartridge paged in as the machine was running it.
 */
@OnlyWith("device-interface2")
public final class Interface2Mapping implements Mapping {

  static final Fixed IF2R = Fixed.of("IF2R", 4);

  static final Layout<Interface2Peripheral> TABLE = Layout.<Interface2Peripheral>of()
      .packedRest(IF2R, 0, Cartridge.SIZE, Field.of("interface2.cartridge",
          slot -> slot.cartridge() == null ? null : slot.cartridge().image(),
          (slot, image) -> slot.inserted(new Cartridge("From a snapshot", image, null))));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(Interface2Peripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(IF2R);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(IF2R) && speccy.peripheralRegistry.find(Interface2Peripheral.class) instanceof Interface2Peripheral slot) slot.plugIn(true);
  }
}

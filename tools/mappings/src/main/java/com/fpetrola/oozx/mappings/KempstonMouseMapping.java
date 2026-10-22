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
import com.fpetrola.oozx.speccy.devices.mouse.KempstonMousePeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The Kempston mouse in an SZX: AMXM, of type 2, with nothing else of it. Where it is and its buttons are the person's
 * hand, not the machine's. A file with an AMXM of type 2 plugs it in.
 */
@OnlyWith("device-mouse")
public final class KempstonMouseMapping implements Mapping {

  static final Fixed AMXM = Fixed.of("AMXM", 7);
  static final int KEMPSTON = 2;

  static final Layout<KempstonMousePeripheral> TABLE = Layout.<KempstonMousePeripheral>of()
      .u8(AMXM, 0, Field.of("mouse.type", mouse -> KEMPSTON, (mouse, type) -> { }));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(KempstonMousePeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(AMXM);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(AMXM, 0, 1) && file.u8(AMXM, 0) == KEMPSTON
        && speccy.peripheralRegistry.find(KempstonMousePeripheral.class) instanceof KempstonMousePeripheral mouse) mouse.plugIn(true);
  }
}

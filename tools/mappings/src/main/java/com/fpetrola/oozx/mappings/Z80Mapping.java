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

import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.snapshots.Z80Format;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;

/** What a Z80 carries of what is plugged in: the AY, in the extended header of a version 2 or 3. */
public final class Z80Mapping implements Mapping {

  static final Layout<AyPeripheral> AY = Layout.<AyPeripheral>of()
      .bytes(Z80Format.EXTENDED, 39, 16, AyFields.REGISTERS).u8(Z80Format.EXTENDED, 38, AyFields.SELECTED);

  @Override
  public Class<? extends DeclaredFormat> format() {
    return Z80Format.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(AyPeripheral.class, AY);
  }
}

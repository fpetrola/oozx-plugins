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
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.scld.ScldPeripheral;
import com.fpetrola.oozx.speccy.devices.scld.TimexMemoryPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * A Timex machine's SCLD in an SZX: what was last written to port 0xf4, the chunks the slot covers, and to port 0xff,
 * what the screen shows. Both are on board, so a file plugs nothing in.
 */
@OnlyWith("device-scld")
public final class ScldMapping implements Mapping {

  static final Fixed SCLD = Fixed.of("SCLD", 2);

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of()
        .on(TimexMemoryPeripheral.class, Layout.<TimexMemoryPeripheral>of()
            .u8(SCLD, 0, Field.of("scld.paging", TimexMemoryPeripheral::wanted, TimexMemoryPeripheral::wanted)))
        .on(ScldPeripheral.class, Layout.<ScldPeripheral>of()
            .u8(SCLD, 1, Field.of("scld.display", ScldPeripheral::register, ScldPeripheral::register)));
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(SCLD);
  }
}

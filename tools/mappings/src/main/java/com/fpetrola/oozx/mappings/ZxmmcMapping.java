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
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.zxmmc.ZxmmcPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/** The ZXMMC in an SZX: ZMMC, which says nothing but that it is plugged in, and plugs it in. */
@OnlyWith("device-zxmmc")
public final class ZxmmcMapping implements Mapping {

  static final Fixed ZMMC = Fixed.of("ZMMC", 0);

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(ZxmmcPeripheral.class, Layout.<ZxmmcPeripheral>of().present(ZMMC));
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(ZMMC);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(ZMMC) && speccy.peripheralRegistry.find(ZxmmcPeripheral.class) instanceof ZxmmcPeripheral mmc) mmc.plugIn(true);
  }
}

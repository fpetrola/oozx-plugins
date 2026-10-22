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
import com.fpetrola.oozx.speccy.devices.covox.CovoxPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/** The Covox in an SZX: COVX, the byte last written to it. A file with COVX plugs it in, on a machine it goes on. */
@OnlyWith("device-covox")
public final class CovoxMapping implements Mapping {

  static final Fixed COVX = Fixed.of("COVX", 4);

  static final Layout<CovoxPeripheral> TABLE = Layout.<CovoxPeripheral>of()
      .u8(COVX, 0, Field.of("covox.written", CovoxPeripheral::written, CovoxPeripheral::written));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(CovoxPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(COVX);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(COVX) && speccy.peripheralRegistry.find(CovoxPeripheral.class) instanceof CovoxPeripheral covox) covox.plugIn(true);
  }
}

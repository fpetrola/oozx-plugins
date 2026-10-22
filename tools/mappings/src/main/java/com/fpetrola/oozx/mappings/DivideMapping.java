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
import com.fpetrola.oozx.formats.PageField;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.divide.DivIdePeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The DivIDE in an SZX: in DIDE the EPROM jumper, whether it is paged in, its control register and its EPROM,
 * packed when the block says so; each page of its RAM in a DIRP. A file with DIDE plugs it in.
 */
@OnlyWith({"device-divide", "device-ide"})
public final class DivideMapping implements Mapping {

  static final Fixed DIDE = Fixed.of("DIDE", 4);
  static final Fixed DIRP = Fixed.pages("DIRP", 0x2000);

  static final Layout<DivIdePeripheral> TABLE = Layout.<DivIdePeripheral>of()
      .bit(DIDE, 0, 0, Field.of("divide.writeProtect", DivIdePeripheral::writeProtect, DivIdePeripheral::setWriteProtect))
      .u8(DIDE, 2, Field.of("divide.control", DivIdePeripheral::control, DivIdePeripheral::control))
      .bit(DIDE, 0, 1, Field.of("divide.paged", DivIdePeripheral::isPaged, DivIdePeripheral::setAutomap))
      .u8(DIDE, 3, Field.of("divide.pages", DivIdePeripheral::ramPages, (divide, pages) -> { }))
      .rest(DIDE, 0, 2, DivIdePeripheral.PAGE_SIZE, Field.of("divide.eprom", DivIdePeripheral::eprom, DivIdePeripheral::eprom))
      .pages(DIRP, DivIdePeripheral::ramPages, PageField.of(DivIdePeripheral::ram, DivIdePeripheral::ram));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(DivIdePeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(DIDE, DIRP);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(DIDE) && speccy.peripheralRegistry.find(DivIdePeripheral.class) instanceof DivIdePeripheral divide) divide.plugIn(true);
  }
}

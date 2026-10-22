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
import com.fpetrola.oozx.speccy.devices.divmmc.DivMmcPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The DivMMC in an SZX: in DMMC the EPROM jumper, whether it is paged in, its control register and its EPROM,
 * packed when the block says so; each page of its RAM in a DMRP. A file with DMMC plugs it in.
 */
@OnlyWith({"device-divmmc", "device-ide"})
public final class DivmmcMapping implements Mapping {

  static final Fixed DMMC = Fixed.of("DMMC", 4);
  static final Fixed DMRP = Fixed.pages("DMRP", 0x2000);

  static final Layout<DivMmcPeripheral> TABLE = Layout.<DivMmcPeripheral>of()
      .bit(DMMC, 0, 0, Field.of("divmmc.writeProtect", DivMmcPeripheral::writeProtect, DivMmcPeripheral::setWriteProtect))
      .u8(DMMC, 2, Field.of("divmmc.control", DivMmcPeripheral::control, DivMmcPeripheral::control))
      .bit(DMMC, 0, 1, Field.of("divmmc.paged", DivMmcPeripheral::isPaged, DivMmcPeripheral::setAutomap))
      .u8(DMMC, 3, Field.of("divmmc.pages", DivMmcPeripheral::ramPages, (divmmc, pages) -> { }))
      .rest(DMMC, 0, 2, DivMmcPeripheral.PAGE_SIZE, Field.of("divmmc.eprom", DivMmcPeripheral::eprom, DivMmcPeripheral::eprom))
      .pages(DMRP, DivMmcPeripheral::ramPages, PageField.of(DivMmcPeripheral::ram, DivMmcPeripheral::ram));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(DivMmcPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(DMMC, DMRP);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(DMMC) && speccy.peripheralRegistry.find(DivMmcPeripheral.class) instanceof DivMmcPeripheral divmmc) divmmc.plugIn(true);
  }
}

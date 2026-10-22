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
import com.fpetrola.oozx.speccy.devices.zxcf.ZxcfPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The ZXCF in an SZX: its upload jumper and its memory register in ZXCF, the register paging as a write to it does,
 * and each bank of its RAM in a CFRP. A file with ZXCF plugs it in.
 */
@OnlyWith({"device-zxcf", "device-ide"})
public final class ZxcfMapping implements Mapping {

  static final Fixed ZXCF = Fixed.of("ZXCF", 4);
  static final Fixed CFRP = Fixed.pages("CFRP", 0x4000);

  static final Layout<ZxcfPeripheral> TABLE = Layout.<ZxcfPeripheral>of()
      .bit(ZXCF, 0, 0, Field.of("zxcf.upload", ZxcfPeripheral::upload, ZxcfPeripheral::setUpload))
      .u8(ZXCF, 2, Field.of("zxcf.memoryControl", ZxcfPeripheral::lastMemoryControl, ZxcfPeripheral::memoryControlWrite))
      .u8(ZXCF, 3, Field.of("zxcf.banks", board -> ZxcfPeripheral.BANKS, (board, banks) -> { }))
      .pages(CFRP, board -> ZxcfPeripheral.BANKS, PageField.of(ZxcfPeripheral::contents, ZxcfPeripheral::contents));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(ZxcfPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(ZXCF, CFRP);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(ZXCF) && speccy.peripheralRegistry.find(ZxcfPeripheral.class) instanceof ZxcfPeripheral board) board.plugIn(true);
  }
}

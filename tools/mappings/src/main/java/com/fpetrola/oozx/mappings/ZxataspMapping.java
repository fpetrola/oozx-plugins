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
import com.fpetrola.oozx.speccy.devices.zxatasp.ZxataspPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The ZXATASP in an SZX: its jumpers and its 8255 in ZXAT, with the bank paged in, or 0xff while its
 * memory is off, and each bank of its RAM in an ATRP. A file with ZXAT plugs it in.
 */
@OnlyWith({"device-zxatasp", "device-ide"})
public final class ZxataspMapping implements Mapping {

  static final Fixed ZXAT = Fixed.of("ZXAT", 8);
  static final Fixed ATRP = Fixed.pages("ATRP", 0x4000);
  /** The bank ZXAT says is paged in while the memory is off. */
  static final int OFF = 0xff;

  static final Layout<ZxataspPeripheral> TABLE = Layout.<ZxataspPeripheral>of()
      .bit(ZXAT, 0, 0, Field.of("zxatasp.upload", ZxataspPeripheral::upload, ZxataspPeripheral::setUpload))
      .bit(ZXAT, 0, 1, Field.of("zxatasp.writeProtect", ZxataspPeripheral::writeProtect, ZxataspPeripheral::setWriteProtect))
      .u8(ZXAT, 2, Field.of("zxatasp.portA", ZxataspPeripheral::portA, ZxataspPeripheral::portA))
      .u8(ZXAT, 3, Field.of("zxatasp.portB", ZxataspPeripheral::portB, ZxataspPeripheral::portB))
      .u8(ZXAT, 4, Field.of("zxatasp.portC", ZxataspPeripheral::portC, ZxataspPeripheral::portC))
      .u8(ZXAT, 5, Field.of("zxatasp.control", ZxataspPeripheral::control, ZxataspPeripheral::control))
      .u8(ZXAT, 6, Field.of("zxatasp.banks", board -> ZxataspPeripheral.BANKS, (board, banks) -> { }))
      .u8(ZXAT, 7, Field.of("zxatasp.pagedIn", board -> board.isPaged() ? board.bank() : OFF,
          (board, bank) -> board.select(bank == OFF ? board.bank() : bank, bank != OFF)))
      .pages(ATRP, board -> ZxataspPeripheral.BANKS, PageField.of(ZxataspPeripheral::contents, ZxataspPeripheral::contents));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(ZxataspPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(ZXAT, ATRP);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(ZXAT) && speccy.peripheralRegistry.find(ZxataspPeripheral.class) instanceof ZxataspPeripheral board) board.plugIn(true);
  }
}

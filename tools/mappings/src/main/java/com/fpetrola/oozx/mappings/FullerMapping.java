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
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.snapshots.Z80Format;
import com.fpetrola.oozx.speccy.devices.fuller.FullerPeripheral;
import dev.crystal.plugins.api.OnlyWith;

/**
 * The Fuller Box in an SZX: its AY is the AY's block, which says in its first bit that the chip is a Fuller Box's. A
 * file that says so plugs one in.
 */
@OnlyWith("device-fuller")
public final class FullerMapping implements Mapping {

  static final Field<FullerPeripheral, Boolean> THERE = Field.of("fuller.pluggedIn", fuller -> true, (fuller, in) -> { });

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(FullerPeripheral.class, Layout.<FullerPeripheral>of().bit(SzxMapping.AY, 0, 0, THERE));
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    plugIf(file.says(SzxMapping.AY, 0, 0x01), speccy);
  }

  static void plugIf(boolean said, Speccy speccy) {
    if (said && speccy.peripheralRegistry.find(FullerPeripheral.class) instanceof FullerPeripheral fuller) fuller.plugIn(true);
  }

  /** The Fuller Box in a Z80, as Spectaculator wrote it: bits 2 and 6 of byte 37, and the AY's registers after it. */
  @OnlyWith("device-fuller")
  public static final class InZ80 implements Mapping {

    @Override
    public Class<? extends DeclaredFormat> format() {
      return Z80Format.class;
    }

    @Override
    public Bindings bindings() {
      return Bindings.of().on(FullerPeripheral.class, Layout.<FullerPeripheral>of()
          .bit(Z80Format.EXTENDED, 37, 2, THERE).bit(Z80Format.EXTENDED, 37, 6, THERE));
    }

    @Override
    public void plug(SnapshotFile file, Speccy speccy) {
      plugIf(file.says(Z80Format.EXTENDED, 37, 0x44), speccy);
    }
  }
}

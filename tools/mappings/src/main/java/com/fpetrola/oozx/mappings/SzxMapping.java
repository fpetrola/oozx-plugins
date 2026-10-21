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
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.devices.ulaplus.UlaPlusPeripheral;

import java.util.List;

/** What an SZX carries of what is plugged in: the AY in its block AY, the ULAplus in PLTT. */
public final class SzxMapping implements Mapping {

  static final Fixed AY = Fixed.of("AY\0\0", 18);
  static final Fixed PLTT = Fixed.of("PLTT", 66);

  static final Layout<AyPeripheral> AY_TABLE = Layout.<AyPeripheral>of()
      .bytes(AY, 2, 16, AyFields.REGISTERS).u8(AY, 1, AyFields.SELECTED);
  static final Layout<UlaPlusPeripheral> ULAPLUS = Layout.<UlaPlusPeripheral>of().encoded(PLTT, 0, 66, new Palette());

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(AyPeripheral.class, AY_TABLE).on(UlaPlusPeripheral.class, ULAPLUS);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(AY, PLTT);
  }

  /** The ULAplus is in the machine if the file has its block, and not if it does not: what the old loader did too. */
  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (speccy.peripheralRegistry.find(UlaPlusPeripheral.class) instanceof UlaPlusPeripheral chip) {
      chip.fitted(file.has(PLTT));
      speccy.peripheralRegistry.update();
    }
  }

  /** PLTT: whether the palette is on, the register last named, and the sixty-four colours as written. */
  static final class Palette implements Encoding<UlaPlusPeripheral> {
    public void read(Bytes bytes, UlaPlusPeripheral chip, SnapshotFile file) {
      int[] colours = new int[64];
      for (int colour = 0; colour < 64; colour++) colours[colour] = bytes.u8(2 + colour);
      chip.asItWas(colours, bytes.u8(0) == 1);
    }

    public void write(UlaPlusPeripheral chip, Bytes bytes, SnapshotFile file) {
      bytes.u8(0, chip.inUse() ? 1 : 0).u8(1, chip.named());
      for (int colour = 0; colour < 64; colour++) bytes.u8(2 + colour, chip.register(colour));
    }

    public List<Object> fields() {
      return List.of();
    }
  }
}

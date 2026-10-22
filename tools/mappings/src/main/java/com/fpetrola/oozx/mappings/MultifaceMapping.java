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
import com.fpetrola.oozx.speccy.devices.multiface.MultifaceModel;
import com.fpetrola.oozx.speccy.devices.multiface.MultifacePeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * A Multiface in an SZX, as Fuse keeps it: MFCE says whether it is the One or a later one, whether it is paged in,
 * J2 (the One's stealth switch, the others' software lockout) and the button's flip-flop, and carries its RAM. A
 * file with MFCE plugs in the one of that kind that fits the machine.
 */
@OnlyWith("device-multiface")
public final class MultifaceMapping implements Mapping {

  static final Fixed MFCE = Fixed.of("MFCE", 2);

  static final Layout<MultifacePeripheral> TABLE = Layout.<MultifacePeripheral>of()
      .u8(MFCE, 0, Field.of("multiface.model", face -> one(face) ? 0 : 1, (face, model) -> { }))
      .bit(MFCE, 1, 0, Field.of("multiface.paged", MultifacePeripheral::isPaged, MultifacePeripheral::pagedIn))
      .bit(MFCE, 1, 2, Field.of("multiface.softwareLockout", face -> !one(face) && !face.isJ2(), (face, out) -> {
        if (!one(face)) face.j2(!out);
      }))
      .bit(MFCE, 1, 3, Field.of("multiface.redButtonOff", face -> !face.redButtonReady(), (face, off) -> face.redButtonReady(!off)))
      .bit(MFCE, 1, 4, Field.of("multiface.stealth", face -> one(face) && !face.isJ2(), (face, stealth) -> {
        if (one(face)) {
          face.setStealth(stealth);
          face.j2(!stealth);
        }
      }))
      .rest(MFCE, 1, 1, Field.of("multiface.ram", MultifacePeripheral::ram, MultifacePeripheral::ram));

  private static boolean one(MultifacePeripheral face) {
    return face.model() == MultifaceModel.ONE;
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(MultifacePeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(MFCE);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (!file.has(MFCE, 0, 1)) return;
    boolean theOne = file.u8(MFCE, 0) == 0;
    speccy.peripheralRegistry.allThatAre(MultifacePeripheral.class).stream()
        .filter(face -> one(face) == theOne && face.fitsOn(speccy.machine.current))
        .findFirst().ifPresent(face -> face.plugIn(true));
  }
}

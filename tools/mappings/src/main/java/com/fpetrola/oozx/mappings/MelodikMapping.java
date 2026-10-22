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
import com.fpetrola.oozx.speccy.devices.melodik.MelodikPeripheral;
import dev.crystal.plugins.api.OnlyWith;

/**
 * The Melodik in an SZX: its AY is the AY's block, which says in its second bit that the chip is a Melodik's. A file
 * that says so fits one, where the machine has no AY of its own.
 */
@OnlyWith("device-melodik")
public final class MelodikMapping implements Mapping {

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(MelodikPeripheral.class, Layout.<MelodikPeripheral>of()
        .bit(SzxMapping.AY, 0, 1, Field.of("melodik.fitted", melodik -> true, (melodik, fitted) -> { })));
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(SzxMapping.AY, 0, 1) && (file.u8(SzxMapping.AY, 0) & 0x02) != 0
        && speccy.peripheralRegistry.find(MelodikPeripheral.class) instanceof MelodikPeripheral melodik) melodik.setFitted(true);
  }
}

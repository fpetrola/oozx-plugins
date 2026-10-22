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
import com.fpetrola.oozx.speccy.devices.uspeech.UspeechPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/** The Currah uSpeech in an SZX: USPE, whether its ROM is paged in. A file with USPE plugs it in. */
@OnlyWith("device-uspeech")
public final class UspeechMapping implements Mapping {

  static final Fixed USPE = Fixed.of("USPE", 1);

  static final Layout<UspeechPeripheral> TABLE = Layout.<UspeechPeripheral>of()
      .flag(USPE, 0, Field.of("uspeech.paged", UspeechPeripheral::isPaged, UspeechPeripheral::pagedIn));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(UspeechPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(USPE);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(USPE) && speccy.peripheralRegistry.find(UspeechPeripheral.class) instanceof UspeechPeripheral speech) speech.plugIn(true);
  }
}

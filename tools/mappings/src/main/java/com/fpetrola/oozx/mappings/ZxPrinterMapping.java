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
import com.fpetrola.oozx.speccy.devices.printer.ZxPrinterPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The ZX Printer in an SZX: ZXPR, which libspectrum writes for every machine, saying in its first bit whether the
 * printer is on. A file that says so plugs in the one that goes on the machine.
 */
@OnlyWith("device-printer")
public final class ZxPrinterMapping implements Mapping {

  static final Fixed ZXPR = Fixed.of("ZXPR", 2);

  static final Layout<ZxPrinterPeripheral> TABLE = Layout.<ZxPrinterPeripheral>of()
      .bit(ZXPR, 0, 0, Field.of("zxprinter.on", printer -> true, (printer, on) -> { }));

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(ZxPrinterPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(ZXPR);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (!file.says(ZXPR, 0, 0x01)) return;
    speccy.peripheralRegistry.allThatAre(ZxPrinterPeripheral.class).stream()
        .filter(printer -> printer.fitsOn(speccy.machine.current)).findFirst().ifPresent(printer -> printer.plugIn(true));
  }
}

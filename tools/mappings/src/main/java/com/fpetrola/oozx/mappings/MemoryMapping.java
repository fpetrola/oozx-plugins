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

import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.speccy.devices.memory.Spec128MemoryPeripheral;
import com.fpetrola.oozx.speccy.devices.memory.SpecPlus3MemoryPeripheral;

/**
 * The memory of the 128K and of the +3 holds nothing a file carries beyond its pages, which every
 * format takes from the memory itself: saying a format left it out would hide what it did leave out.
 */
public final class MemoryMapping implements Mapping {

  @Override
  public Class<? extends DeclaredFormat> format() {
    return DeclaredFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().quiet(Spec128MemoryPeripheral.class, SpecPlus3MemoryPeripheral.class);
  }
}

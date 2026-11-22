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

package com.fpetrola.oozx.speccy.devices.printer;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.devices.Equipment;

@Offers(value = "Printers and pointers/ZX Printer",
    description = "Sinclair's thermal printer: the belt and the paper, the printout burnt in dots; tear off, save as a picture",
    known = "zxdb:1000472", icon = "printer")
public class PrinterEquipment implements Equipment {
  public String name() {
    return "ZX Printer";
  }

  public DeviceFrame<?> open() {
    return new PrinterInternalFrame();
  }
}

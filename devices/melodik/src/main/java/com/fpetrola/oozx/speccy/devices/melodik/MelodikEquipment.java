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

package com.fpetrola.oozx.speccy.devices.melodik;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.devices.Equipment;
import com.fpetrola.oozx.speccy.devices.ay.AyFrame;
import dev.crystal.plugins.api.Offers;

@Offers(value = "Things that make a sound/Melodik",
    description = "The 128's sound chip in a box, so 128K music plays on a 48K", known = "zxdb:1001155", icon = "music")
public class MelodikEquipment implements Equipment {
  public String name() {
    return "Melodik";
  }

  public DeviceFrame<?> open() {
    return new AyFrame<>("Melodik", MelodikPeripheral.class,
        "An AY-3-8912 decoded on the same ports as the 128's, for a machine that has no chip of its own.");
  }
}

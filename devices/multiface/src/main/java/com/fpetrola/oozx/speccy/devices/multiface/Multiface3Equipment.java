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
package com.fpetrola.oozx.speccy.devices.multiface;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.devices.Equipment;
import dev.crystal.plugins.api.Offers;

@Offers(value = "Cartridges, freezers and colours/Multiface 3",
    description = "Romantic Robot's red button for the +3: stop any program, save it, poke it", known = "zxdb:1000274", icon = "snowflake")
public class Multiface3Equipment implements Equipment {
  public String name() {
    return MultifaceModel.M3.title;
  }

  public DeviceFrame<?> open() {
    return new MultifaceFrame(MultifaceModel.M3);
  }
}

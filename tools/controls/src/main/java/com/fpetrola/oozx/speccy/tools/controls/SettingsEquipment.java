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

package com.fpetrola.oozx.speccy.tools.controls;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.DeskEquipment;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.Icon;
import javax.swing.JInternalFrame;

@Offers("Change the settings")
public class SettingsEquipment implements DeskEquipment {
  public String name() {
    return "Settings";
  }

  public JInternalFrame open() {
    return new SettingsInternalFrame();
  }

  public String keeps() {
    return "SETTINGS";
  }

  @Override
  public Icon icon() {
    return Widgets.loadIcon("2699.svg");
  }
}

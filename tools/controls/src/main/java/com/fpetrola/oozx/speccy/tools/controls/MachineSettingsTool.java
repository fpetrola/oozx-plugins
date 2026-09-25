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

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

/** Los ajustes de esta maquina, pegados a su ventana. */
@Offers("Change the settings of this machine")
public class MachineSettingsTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("2699.svg");
  }

  public String tooltip() {
    return "Settings of this machine, clipped onto it";
  }

  public int place() {
    return 25;
  }

  public void use(EmulatorWindow window) {
    SettingsInternalFrame settings = new SettingsInternalFrame();
    settings.setLocation(80, 80);
    Desk.theOne().place(settings);
    settings.setMachineWindow((javax.swing.JInternalFrame) window);
  }
}

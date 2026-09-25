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

import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

/** Apagar y prender la maquina. */
@Offers("Reset the machine")
public class ResetTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F504.svg");
  }

  public String tooltip() {
    return "Reset the machine, as if it had just been switched on";
  }

  public int place() {
    return 30;
  }

  public void use(EmulatorWindow window) {
    TheMachine.of(window).resetEmulation();
  }
}

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

package com.fpetrola.oozx.speccy.tools.games;

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

/** Lo que el catalogo sabe del juego que esta corriendo. */
public class GameDetailsTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("E259.svg");
  }

  public String tooltip() {
    return "View Game Details";
  }

  public int place() {
    return 90;
  }

  public void use(EmulatorWindow window) {
    Desk.theOne().showDetails(theGameIn(window));
  }

  /** El juego de donde salio, y si no se abrio desde uno, el archivo que tiene cargado. */
  static Desk.Game theGameIn(EmulatorWindow window) {
    return window.game() != null ? window.game()
        : new Desk.Game(window.machine().control.getFilename(), null, null, null);
  }
}

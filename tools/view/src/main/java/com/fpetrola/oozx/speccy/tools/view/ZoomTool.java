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

package com.fpetrola.oozx.speccy.tools.view;

import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.SwingUtilities;

/** Una, dos o tres veces el tamanio de la pantalla. */
public class ZoomTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("E243.svg");
  }

  public String tooltip() {
    return "Zoom: 1x, 2x, 3x";
  }

  public int place() {
    return 60;
  }

  public void use(EmulatorWindow window) {
    if (window.picture() instanceof SpeccyScreen screen) {
      screen.setZoom(screen.getZoom() >= 3 ? 1 : screen.getZoom() + 1);
      // La ventana se ajusta a la pantalla nueva: la interna si es una, si no la de afuera.
      java.awt.Container frame = SwingUtilities.getAncestorOfClass(javax.swing.JInternalFrame.class, screen);
      if (frame instanceof javax.swing.JInternalFrame inner) inner.pack();
      else if (SwingUtilities.getWindowAncestor(screen) != null) SwingUtilities.getWindowAncestor(screen).pack();
    }
  }
}

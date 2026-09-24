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

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;

/** Como se ve la pantalla: escalado, television y color. */
public class ScreenSettingsTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F39B.svg");
  }

  public String tooltip() {
    return "Screen - scaling, television and colour";
  }

  public int place() {
    return 75;
  }

  public void use(EmulatorWindow window) {
    JInternalFrame frame = (JInternalFrame) window;
    if (!(Desk.theOne().coreOf(frame).getPanel() instanceof SpeccyScreen screen)) {
      JOptionPane.showMessageDialog(null, "This emulator has no adjustable screen.",
          "Screen", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Desk.theOne().place(new ScreenSettingsInternalFrame(frame.getTitle(),
        screen.getScreenSettings()));
  }
}

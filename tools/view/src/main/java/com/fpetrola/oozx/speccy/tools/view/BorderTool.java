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

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;

/**
 * El borde de la pantalla. Un boton que queda apretado mientras se ve, como el borde, que esta o
 * no esta. Lo que se elige queda para las maquinas que se abran despues.
 */
public class BorderTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("border-stripes.svg");
  }

  public String tooltip() {
    return "Show the Border";
  }

  public int place() {
    return 50;
  }

  public void use(EmulatorWindow window) {
    show(window, !shown(window));
  }

  public AbstractButton button(EmulatorWindow window) {
    JToggleButton button = new JToggleButton(icon());
    button.setToolTipText(tooltip());
    button.setSelected(shown(window));
    button.addActionListener(pressed -> show(window, button.isSelected()));
    return button;
  }

  public JMenuItem menuItem(EmulatorWindow window) {
    JCheckBoxMenuItem border = new JCheckBoxMenuItem("Border", shown(window));
    border.addActionListener(chosen -> show(window, border.isSelected()));
    return border;
  }

  private static boolean shown(EmulatorWindow window) {
    return window.picture() instanceof SpeccyScreen screen && screen.getScreenSettings().isBorder();
  }

  private static void show(EmulatorWindow window, boolean on) {
    window.machine().control.setGeneralOption("border", on);
    Desk.theOne().rememberScreen("border", String.valueOf(on));
  }
}

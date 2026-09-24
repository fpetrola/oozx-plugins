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

import com.fpetrola.oozx.EmulatorControl;
import com.fpetrola.oozx.EmulatorListener;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/** Parar y seguir: un boton que muestra lo que va a hacer, y en la barra de estado lo que esta haciendo. */
public class PauseTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("23F8.svg");
  }

  public String tooltip() {
    return "Pause";
  }

  public int place() {
    return 20;
  }

  public void use(EmulatorWindow window) {
    TheMachine.of(window).pauseEmulation();
  }

  public AbstractButton button(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    JButton button = new JButton();
    Runnable shows = () -> {
      boolean paused = machine.isPaused();
      button.setIcon(Widgets.loadIcon(paused ? "25B6.svg" : "23F8.svg"));
      button.setToolTipText(paused ? "Continue" : "Pause");
    };
    shows.run();
    button.addActionListener(pressed -> use(window));
    whenPausedOrNot(machine, shows);
    return button;
  }

  public JMenuItem menuItem(EmulatorWindow window) {
    JCheckBoxMenuItem paused = new JCheckBoxMenuItem("Pause", TheMachine.of(window).isPaused());
    paused.addActionListener(chosen -> use(window));
    return paused;
  }

  public JComponent status(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    JLabel shown = new JLabel();
    Runnable shows = () -> {
      boolean paused = machine.isPaused();
      shown.setIcon(Widgets.loadIcon(paused ? "23F8.svg" : "25B6.svg"));
      shown.setToolTipText(paused ? "Paused" : "Running");
    };
    shows.run();
    whenPausedOrNot(machine, shows);
    return shown;
  }

  private static void whenPausedOrNot(EmulatorControl machine, Runnable shows) {
    machine.addEmulatorListener(new EmulatorListener() {
      public void onPauseStateChanged(boolean paused) {
        SwingUtilities.invokeLater(shows);
      }
    });
  }
}

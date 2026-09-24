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
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/** La maquina tal como esta, escrita en un archivo que se abre despues como cualquier snapshot. */
public class SnapshotTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("E260.svg");
  }

  public String tooltip() {
    return "Save this machine exactly as it is, to open again later";
  }

  public int place() {
    return 80;
  }

  public void use(EmulatorWindow window) {
    var machine = window.machine().control;
    JFileChooser chooser = new JFileChooser();
    String name = machine.getFilename();
    name = name == null ? "snapshot" : new java.io.File(name).getName().replaceAll("\\.[^.]*$", "");
    chooser.setSelectedFile(new java.io.File(name + ".z80"));
    if (chooser.showSaveDialog(window.picture()) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    java.io.File file = chooser.getSelectedFile();
    machine.saveState(file.getAbsolutePath());
    JOptionPane.showMessageDialog(window.picture(), file.getName() + " written.\n\n"
            + "Open it the way you would open a tape and the machine comes back as it is now.",
        "Snapshot", JOptionPane.INFORMATION_MESSAGE);
  }
}

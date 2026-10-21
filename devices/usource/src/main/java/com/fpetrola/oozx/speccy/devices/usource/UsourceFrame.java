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

package com.fpetrola.oozx.speccy.devices.usource;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Color;

/** The µSource on the desk: its ROM, chosen here and read at a hard reset, and a light that is on while it is in. */
public class UsourceFrame extends DeviceFrame<UsourcePeripheral> {

  private static final int REFRESH_MILLIS = 100;

  private final JButton rom = Widgets.iconButton("rom.svg", "ROM...",
      "Choose the µSource's ROM, which is not something this emulator can ship");
  private final JLabel led = new JLabel("●");
  private final JLabel status = new JLabel();
  private final Timer refresh = new Timer(REFRESH_MILLIS, e -> refresh());

  public UsourceFrame() {
    super("Currah µSource", UsourcePeripheral.class);
    setSize(380, 140);
    rom.addActionListener(e -> chooseRoms("The µSource's ROM (8K)"));
    led.setToolTipText("Lit while the µSource's ROM is over the machine's");
    controls.add(rom);
    controls.add(led);
    controls.add(status);
    assemble(new JLabel("<html>RANDOMIZE USR 11182 brings the assembler and the Forth in, and takes them out again.</html>"));
    addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosed(InternalFrameEvent e) {
        refresh.stop();
      }
    });
    refresh.start();
    plugged(null);
  }

  @Override
  protected void plugged(UsourcePeripheral device) {
    rom.setEnabled(device != null);
    refresh();
  }

  private void refresh() {
    UsourcePeripheral device = device();
    if (device == null) {
      led.setForeground(Color.GRAY);
      status.setText("not plugged into a machine");
      return;
    }
    led.setForeground(device.isPaged() ? Color.RED : Color.GRAY);
    status.setText(device.isAvailable() ? (device.isPaged() ? "paged in" : "ready")
        : "no ROM: " + machine().roms.nameOf(device));
  }

  @Override
  protected String expandTip() {
    return "Show how to bring it in, or just the ROM and the light";
  }

  @Override
  protected String attachTip() {
    return "Clip this onto the machine's window, which is what plugs the µSource in";
  }
}

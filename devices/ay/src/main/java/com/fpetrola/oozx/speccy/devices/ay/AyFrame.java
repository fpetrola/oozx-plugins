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

package com.fpetrola.oozx.speccy.devices.ay;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.peripherals.Pluggable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Color;
import java.awt.GridLayout;

/** A box with an AY in it, on the desk: a light while a program writes to the chip, and what the box is. */
public class AyFrame<P extends AyPeripheral & Pluggable> extends DeviceFrame<P> {

  private static final int REFRESH_MILLIS = 80;

  private final JLabel chip = new JLabel("●");
  private final JLabel written = new JLabel();
  private final Timer refresh = new Timer(REFRESH_MILLIS, e -> refresh());
  private long seen;

  /** @param more what else the box has besides the chip, beside its light */
  public AyFrame(String title, Class<P> kind, String what, JComponent... more) {
    super(title, kind);
    setSize(420, 160);
    chip.setToolTipText("Lit while a program is writing to the sound chip");
    controls.add(chip);
    for (JComponent one : more) controls.add(one);
    JPanel inside = new JPanel(new GridLayout(0, 1));
    inside.add(written);
    inside.add(new JLabel("<html>" + what + "</html>"));
    assemble(inside);
    addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosed(InternalFrameEvent e) {
        refresh.stop();
      }
    });
    refresh.start();
    refresh();
  }

  @Override
  protected void plugged(P device) {
    refresh();
  }

  private void refresh() {
    P box = device();
    if (box == null) {
      chip.setForeground(Color.GRAY);
      written.setText("not plugged into a machine");
      return;
    }
    long now = box.writes();
    chip.setForeground(now != seen ? new Color(0x30c030) : Color.GRAY);
    seen = now;
    written.setText("register writes: " + now);
  }

  @Override
  protected String expandTip() {
    return "Show what the box is, or just its lights";
  }

  @Override
  protected String attachTip() {
    return "Clip this onto the machine's window, which is what plugs the box in";
  }
}

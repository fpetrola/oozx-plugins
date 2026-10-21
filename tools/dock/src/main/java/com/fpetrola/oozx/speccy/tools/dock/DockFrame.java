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

package com.fpetrola.oozx.speccy.tools.dock;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.MachineFrame;
import com.fpetrola.oozx.speccy.devices.MediaSlot;
import com.fpetrola.oozx.speccy.devices.Opens;
import com.fpetrola.oozx.speccy.machine.DockCartridge;
import com.fpetrola.oozx.speccy.machine.Tc2068;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;

/**
 * A Timex's dock on the desk: the cartridge in it, put in here or by opening a .dck, and the
 * machine starting again on it, since a cartridge went in with the machine off.
 */
public class DockFrame extends MachineFrame implements Opens {

  private final MediaSlot slot = new MediaSlot("cartridge", "cartridge.svg",
      new FileNameExtensionFilter("Timex dock cartridge", "dck"), this::open, this::eject);

  public DockFrame() {
    super("Timex dock");
    setSize(420, 140);
    controls.add(slot);
    assemble(new JLabel("<html>The TC2068 and the TS2068 have a dock: putting a cartridge in, or taking it out, "
        + "starts the machine again.</html>"));
  }

  @Override
  protected void machineChanged(Speccy was, Speccy now) {
    Tc2068 timex = dock();
    slot.show(timex == null || timex.cartridge() == null ? null : timex.cartridge().name());
  }

  /** The dock of the machine this is clipped to, or none when that machine has no dock. */
  private Tc2068 dock() {
    return machine() != null && machine().machine.current instanceof Tc2068 timex ? timex : null;
  }

  @Override
  public void open(File file) {
    Tc2068 timex = dock();
    if (timex == null) {
      JOptionPane.showMessageDialog(this, "This machine has no dock: a Timex TC2068 or TS2068 has one.");
      return;
    }
    try {
      DockCartridge cartridge = DockCartridge.read(file);
      startOn(timex, cartridge);
      slot.show(cartridge.name());
    } catch (IOException notACartridge) {
      JOptionPane.showMessageDialog(this, notACartridge.getMessage());
    }
  }

  private void eject() {
    Tc2068 timex = dock();
    if (timex != null) {
      startOn(timex, null);
    }
    slot.show(null);
  }

  private void startOn(Tc2068 timex, DockCartridge cartridge) {
    Speccy speccy = machine();
    speccy.loop.later(() -> {
      timex.insert(cartridge);
      speccy.machine.reset(false);
    });
  }

  @Override
  public boolean empty() {
    Tc2068 timex = dock();
    return timex == null || timex.cartridge() == null;
  }

  @Override
  protected String expandTip() {
    return "Show what the dock is, or just the slot";
  }

  @Override
  protected String attachTip() {
    return "Clip this onto a Timex's window, whose dock it is";
  }
}

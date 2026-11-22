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
package com.fpetrola.oozx.speccy.devices.bettersound;

import com.fpetrola.oozx.speccy.devices.DeviceFrame;
import com.fpetrola.oozx.speccy.devices.Equipment;
import com.fpetrola.oozx.speccy.windows.KnobRows;
import dev.crystal.plugins.api.Offers;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;

@Offers(value = "Things that make a sound/Better sound",
    description = "The speaker's music and effects played on an AY, with an envelope on every note, harmony and a room",
    icon = "music")
public class BetterSoundEquipment implements Equipment {
  public String name() {
    return "Better sound";
  }

  public DeviceFrame<?> open() {
    JPanel knobs = new JPanel(new BorderLayout());
    return new DeviceFrame<BetterSoundPeripheral>("Better sound", BetterSoundPeripheral.class) {
      {
        plugged(null);
        assemble(knobs);
        setSize(420, 520);
      }

      @Override
      protected void plugged(BetterSoundPeripheral device) {
        knobs.removeAll();
        knobs.add(device == null ? new JLabel("Clip it onto a machine to hear its speaker through the AY")
            : new JScrollPane(new KnobRows(null).of(device.knobs())));
        knobs.revalidate();
        knobs.repaint();
      }

      @Override
      protected String expandTip() {
        return "Show the knobs, or just the bar";
      }

      @Override
      protected String attachTip() {
        return "Clip this onto the machine's window, which is what makes it play the speaker";
      }
    };
  }
}

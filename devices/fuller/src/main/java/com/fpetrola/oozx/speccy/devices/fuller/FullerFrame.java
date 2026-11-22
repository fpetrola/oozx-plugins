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
package com.fpetrola.oozx.speccy.devices.fuller;

import com.fpetrola.oozx.speccy.devices.ay.AyFrame;
import com.fpetrola.oozx.speccy.modules.input.Input;
import com.fpetrola.oozx.speccy.modules.joystick.Joystick.JoystickType;
import dev.plugdesk.desk.Widgets;

import javax.swing.JToggleButton;

/**
 * The Fuller Box on the desk: the sound chip's box, and the joystick socket, where the keyboard's joystick can be
 * plugged in.
 */
public class FullerFrame extends AyFrame<FullerPeripheral> {

  private final JToggleButton socket;

  public FullerFrame() {
    this(Widgets.iconToggle("1F579.svg", "Joystick",
        "The keyboard's joystick, plugged into the Fuller's socket and read on port 0x7f"));
  }

  private FullerFrame(JToggleButton socket) {
    super("Fuller Box", FullerPeripheral.class,
        "An AY-3-8912 on ports 0x3f and 0x5f, for music written for it on a 48K, and a joystick read on 0x7f.", socket);
    this.socket = socket;
    socket.addActionListener(e -> {
      if (machine() != null) {
        Input.of(machine()).setup().keyboard().setOutput(socket.isSelected() ? JoystickType.JOYSTICK_TYPE_FULLER : JoystickType.JOYSTICK_TYPE_NONE);
      }
    });
    plugged(null);
  }

  @Override
  protected void plugged(FullerPeripheral device) {
    super.plugged(device);
    socket.setEnabled(device != null);
    socket.setSelected(device != null
        && Input.of(machine()).setup().keyboard().getOutput() == JoystickType.JOYSTICK_TYPE_FULLER);
  }
}

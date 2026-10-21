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

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.MockEmulatorCore;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * The mute button says what the machine is doing, whoever muted it: a session that comes back
 * muted, or the settings window, tell the machine and not the button.
 */
class TheMuteButtonSaysWhatTheMachineDoesTest {

  @Test
  void aMachineMutedElsewhereShowsMutedOnItsButton() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    boolean[] muted = {false};
    speccy.control = new MockEmulatorCore(new JPanel()) {
      public boolean isMuted() {
        return muted[0];
      }
    };
    AbstractButton button = new SoundTool().button(new EmulatorWindow() {
      public Speccy machine() {
        return speccy;
      }

      public JComponent picture() {
        return new JPanel();
      }
    });
    Icon sounding = button.getIcon();
    assertEquals("Mute Sound", button.getToolTipText());

    muted[0] = true;

    assertEquals("Unmute Sound", button.getToolTipText());
    assertNotSame(sounding, button.getIcon());
    speccy.end();
  }
}

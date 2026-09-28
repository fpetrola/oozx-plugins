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

package model.tests.devices;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.machine.Spec128;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.parts.Visitable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The AY of a 128 is met when the machine is walked, and says what its registers hold. */
class TheAyShowsWhatItHoldsTest {

  Speccy speccy;
  AyPeripheral ay;

  @BeforeEach
  void a128() {
    speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    speccy.machine.select(speccy.machine.model(Spec128.class));
    ay = (AyPeripheral) speccy.peripheralRegistry.find(AyPeripheral.class);
  }

  @Test
  void itIsMetOnA128() {
    List<Visitable> parts = new ArrayList<>();
    speccy.accept(parts::add);
    assertTrue(parts.contains(ay));
  }

  @Test
  void whatThePortsWriteIsWhatItHolds() {
    speccy.ports.write(0xfffd, (byte) 7);
    speccy.ports.write(0xbffd, (byte) 0x38);
    assertEquals(7, ay.selected());
    assertEquals(0x38, ay.register(7));
  }

  @Test
  void aRegisterPutKeepsOnlyItsBitsAndLeavesTheSelectionAlone() {
    ay.select(14);
    ay.register(1, 0xff);
    assertEquals(0x0f, ay.register(1));
    assertEquals(14, ay.selected());
  }
}

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

package model.tests.formats;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.snapshots.EzxFormat;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exolon as the Spec256 games keep it, an EZX packed in two NRV2E blocks. What it should hold was
 * taken from unpacking it by hand: the processor where it stopped, and the RAM a 48K has.
 */
class AnEzxFromEmuZWinIsReadTest {

  static final Path EXOLON = Path.of("src/test/resources/ezx/exolon.ezx");

  @Test
  void itsProcessorAndItsRamAreThoseItWasSavedWith() throws Exception {
    byte[] bytes = Files.readAllBytes(EXOLON);
    byte[] ram = new EzxFormat().ram(bytes).orElseThrow();
    assertEquals("9f38429e5de45306d3d26a03326cd5f6373cca71a435500c01216068938f088e",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ram)), "banks 0, 2 and 5");
  }

  @Test
  void itCarriesTheColoursASpec256GameKeepsInFilesBesideIt() throws Exception {
    java.util.Map<String, byte[]> carried = new EzxFormat().carried(Files.readAllBytes(EXOLON));
    assertEquals(3 * 8 * 16384, carried.get("gfx").length, "a colour for each bit from 0x4000 to the top");
    assertTrue(new String(carried.get("cfg"), java.nio.charset.StandardCharsets.ISO_8859_1).contains("UpColorsMixed="),
        "and the rules it is painted by");
  }

  @Test
  void itBecomesTheFortyEightItWasLockedToWhereItStopped() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    try {
      Snapshots.of(speccy).load(EXOLON.toString());
      String machine = MachineDescription.of(speccy);
      assertTrue(machine.contains("machine = SPECTRUM48K"), machine);
      assertTrue(machine.contains("pc = 0xb7b4") && machine.contains("sp = 0xffde"), machine);
    } finally {
      speccy.end();
    }
  }
}

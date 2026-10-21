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
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The net over the machine: every fixture loaded into a fresh machine, and the machine described,
 * as it was when these goldens were written. What a format leaves in the machine is what matters,
 * so this is what every format that replaces another is held to. With
 * -Dsnapshots.writeMachineGoldens=true it writes them instead.
 */
class EveryFixtureLoadsIntoTheMachineAsItDidTest {

  static final boolean WRITING = Boolean.getBoolean("snapshots.writeMachineGoldens");

  static String loaded(Path fixture) {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    try {
      Snapshots.of(speccy).load(fixture.toString());
      return MachineDescription.of(speccy);
    } catch (RuntimeException refused) {
      return "refused\n";
    } finally {
      speccy.end();
    }
  }

  @TestFactory
  Stream<DynamicTest> eachFixtureLeavesTheMachineAsItDid() {
    return Fixtures.all().stream().map(fixture -> DynamicTest.dynamicTest(Fixtures.nameOf(fixture), () -> {
      Path golden = Fixtures.golden(fixture, "machine");
      String now = loaded(fixture);
      if (WRITING) {
        Fixtures.write(golden, now);
      } else {
        assertEquals(Fixtures.text(golden), now, Fixtures.nameOf(fixture));
      }
    }));
  }
}

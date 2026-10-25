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
import com.fpetrola.oozx.speccy.devices.spec256.Planes;
import com.fpetrola.oozx.speccy.devices.spec256.Spec256Peripheral;
import com.fpetrola.oozx.speccy.machine.Spec48;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A session: colours in a file beside a snapshot put the machine on the nine processors, and
 * everything that is not that game puts it back where it was.
 * <p>
 * No game's files here. A 48K snapshot is twenty-seven bytes of registers and the machine's RAM,
 * and colours are eight bytes for each of those, so both are written on the spot.
 */
class TheSessionOfAGameTest extends MachineTest {
  @TempDir
  Path where;

  private Speccy speccy;

  private Speccy machine() {
    speccy = silentMachine();
    select(speccy, speccy.machine.model(Spec48.class));
    return speccy;
  }

  private String snapshot(String name) throws IOException {
    return snapshot(name, 27 + 0xc000);
  }

  /** As long as a 128K one is, with the PC and the paging after the first three banks, and the other five: 131103. */
  private String snapshot(String name, int length) throws IOException {
    byte[] sna = new byte[length];
    sna[23] = 0x00;
    sna[24] = 0x40;                                  // the stack points somewhere harmless
    Path file = where.resolve(name + ".sna");
    Files.write(file, sna);
    return file.toString();
  }

  private void coloursFor(String name, int length) throws IOException {
    Files.write(where.resolve(name + ".GFX"), new byte[length]);
  }

  private void load(String url) {
    Snapshots.of(speccy).load(url);
    speccy.loop.applyWhatWasDeferred();
  }

  private Spec256Peripheral session() {
    return (Spec256Peripheral) speccy.peripheralRegistry.find(Spec256Peripheral.class);
  }

  @Test
  void aSnapshotWithItsColoursBesideItPutsTheMachineOnTheNine() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH);

    load(snapshot("game"));

    assertEquals("Spec256", speccy.processors.current(), "the colours are there, so the machine carries them");
    assertEquals("game.GFX", session().playing(), "and it says which game's they are");
    assertNotEquals("Spec256", was, "which is not where it started");
  }

  /**
   * A game for another machine loaded while one plays: the change of machine ends the first, and the second is on
   * the nine all the same. What is left when it ends is where the machine was before either.
   */
  @Test
  void aGameForAnotherMachineLoadedWhileOnePlaysIsOnTheNineToo() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH);
    load(snapshot("game"));
    coloursFor("bigger", Planes.LENGTH);

    load(snapshot("bigger", 131103));

    assertTrue(speccy.machine.current.pagesThrough7ffd(), "it is a 128K now");
    assertEquals(java.util.List.of("Spec256", "bigger.GFX"), java.util.List.of(speccy.processors.current(), session().playing()));
    load(snapshot("plain"));
    assertEquals(was, speccy.processors.current(), "back where it was before the first, not on the nine");
  }

  /**
   * A game loaded while another plays goes on as it does on a machine that has just opened it: the nine are built
   * for it, not kept from the one before.
   */
  @Test
  void aGameLoadedWhileAnotherPlaysGoesOnAsOnAMachineThatJustOpenedIt() throws IOException {
    Path exolon = Files.copy(Path.of("../snapshots/src/test/resources/ezx/exolon.ezx"), where.resolve("exolon.ezx"));
    Speccy again = machine();
    load(exolon.toString());
    runFrames(again, 400);
    load(exolon.toString());
    Speccy fresh = machine();
    load(exolon.toString());

    runFrames(again, 10);
    runFrames(fresh, 10);

    assertArrayEquals(colours(fresh), colours(again));
  }

  private static byte[] colours(Speccy speccy) {
    return ((Spec256Peripheral) speccy.peripheralRegistry.find(Spec256Peripheral.class)).files().get("gfx");
  }

  @Test
  void anEzxThatCarriesItsColoursPutsTheMachineOnTheNineWithNothingBesideIt() throws IOException {
    machine();
    Path exolon = where.resolve("exolon.ezx");
    Files.copy(Path.of("../snapshots/src/test/resources/ezx/exolon.ezx"), exolon);

    load(exolon.toString());

    assertEquals("Spec256", speccy.processors.current(), "the colours came inside the file");
    assertEquals("exolon.ezx", session().playing(), "and they are that file's");
  }

  @Test
  void aSnapshotThatBringsNoColoursLeavesTheMachineWhereItWas() throws IOException {
    machine();
    String was = speccy.processors.current();

    load(snapshot("plain"));

    assertEquals(was, speccy.processors.current());
    assertNull(session().playing());
  }

  @Test
  void theMachineGoesBackToTheProcessorItWasOnWhenTheNextSnapshotBringsNothing() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH);
    load(snapshot("game"));

    load(snapshot("plain"));

    assertEquals(was, speccy.processors.current(), "back where it came from, not on some default");
    assertNull(session().playing());
  }

  @Test
  void aResetEndsTheSession() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH);
    load(snapshot("game"));

    speccy.machine.reset(true);
    speccy.loop.applyWhatWasDeferred();

    assertEquals(was, speccy.processors.current());
    assertNull(session().playing());
  }

  @Test
  void aChangeOfMachineEndsTheSession() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH);
    load(snapshot("game"));

    select(speccy, speccy.machine.model(com.fpetrola.oozx.speccy.machine.Spec128.class));
    speccy.loop.applyWhatWasDeferred();

    assertEquals(was, speccy.processors.current());
    assertNull(session().playing());
  }

  @Test
  void aFileOfTheWrongSizeBesideASnapshotIsNotAGamesColours() throws IOException {
    machine();
    String was = speccy.processors.current();
    coloursFor("game", Planes.LENGTH - 1);

    load(snapshot("game"));

    assertEquals(was, speccy.processors.current(), "a machine that would not paint right does not start");
    assertNull(session().playing());
  }
}

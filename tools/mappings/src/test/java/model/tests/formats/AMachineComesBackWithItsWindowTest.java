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
import com.fpetrola.oozx.api.KnowsTheGames;
import com.fpetrola.oozx.config.Configuration;
import com.fpetrola.oozx.config.RomFiles;
import com.fpetrola.oozx.config.Settings;
import com.fpetrola.oozx.formats.CoreFields;
import com.fpetrola.oozx.mappings.SzxSession;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.Equipment;
import com.fpetrola.oozx.speccy.config.OOZxConfiguration;
import com.fpetrola.oozx.speccy.devices.MachineFrame;
import com.fpetrola.oozx.speccy.devices.covox.CovoxPeripheral;
import com.fpetrola.oozx.speccy.devices.disk.Beta128Peripheral;
import com.fpetrola.oozx.speccy.devices.divide.DivIdePeripheral;
import com.fpetrola.oozx.speccy.devices.divmmc.DivMmcPeripheral;
import com.fpetrola.oozx.speccy.devices.ide.IdeChannel;
import com.fpetrola.oozx.speccy.devices.ide.IdeInterface;
import com.fpetrola.oozx.speccy.devices.disk.Disk;
import com.fpetrola.oozx.speccy.devices.interface2.Cartridge;
import com.fpetrola.oozx.speccy.devices.interface2.Interface2Peripheral;
import com.fpetrola.oozx.speccy.devices.melodik.MelodikPeripheral;
import com.fpetrola.oozx.speccy.devices.simpleide.SimpleIdePeripheral;
import com.fpetrola.oozx.speccy.devices.mouse.MouseEquipment;
import com.fpetrola.oozx.speccy.devices.printer.PrinterEquipment;
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumEquipment;
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumPeripheral;
import com.fpetrola.oozx.speccy.devices.zxatasp.ZxataspPeripheral;
import com.fpetrola.oozx.speccy.devices.zxcf.ZxcfPeripheral;
import com.fpetrola.oozx.speccy.devices.zxmmc.ZxmmcPeripheral;
import com.fpetrola.oozx.speccy.machine.Pentagon;
import com.fpetrola.oozx.speccy.machine.SpecPlus3;
import com.fpetrola.oozx.speccy.machine.Tc2048;
import com.fpetrola.oozx.speccy.modules.display.Picture;
import com.fpetrola.oozx.speccy.modules.input.Input;
import com.fpetrola.oozx.speccy.modules.joystick.Joystick;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.tape.Tape;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.MockEmulatorCore;
import com.fpetrola.oozx.speccy.peripherals.Peripheral;
import com.fpetrola.oozx.speccy.peripherals.Pluggable;
import com.fpetrola.oozx.speccy.pokes.PokFile;
import com.fpetrola.oozx.speccy.pokes.PokesTool;
import com.fpetrola.oozx.speccy.screen.SpeccyScreen;
import com.fpetrola.oozx.speccy.tools.games.GamesFrame;
import com.fpetrola.oozx.speccy.windows.AttachedFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A machine's window kept as a file of the session and opened again: the machine from the file as
 * any file opens, with its settings and what was plugged into it, and then how its window showed the
 * picture, whether it was paused, the file it came from, the pokes applied to it and the equipment
 * clipped onto it. Another emulator reads the same file. And the whole desk, kept and opened again as
 * the desk does it.
 */
class AMachineComesBackWithItsWindowTest {

  /** A machine's window, as far as the session can tell: where it is, which machine it shows, and how. */
  static final class Window extends JInternalFrame implements EmulatorWindow {
    private final Speccy speccy;
    private final SpeccyScreen screen;

    Window(Speccy speccy) {
      this.speccy = speccy;
      this.screen = new SpeccyScreen(speccy.picture.pixels, Picture.STRIDE);
    }

    public SpeccyScreen picture() {
      return screen;
    }

    public Speccy machine() {
      return speccy;
    }
  }

  /** What the desk's machine does with a pause and a file, which is all of it the session asks. */
  static final class Control extends MockEmulatorCore {
    private boolean paused;
    private String file;

    Control() {
      super(new JPanel());
    }

    public boolean isPaused() {
      return paused;
    }

    public void setGeneralOption(String option, Object value) {
      if (option.equals("pause")) paused = (Boolean) value;
      else super.setGeneralOption(option, value);
    }

    public String getFilename() {
      return file;
    }

    public void setFilename(String file) {
      this.file = file;
    }
  }

  /**
   * A desk offering a few pieces of equipment and the game browser, on a desktop of its own, that
   * keeps the windows it opened of each: what the application's does with them and nothing more. It
   * hands back the ones clipped onto a machine in no particular order, as the application's do:
   * newest first here.
   */
  record Desktop(JDesktopPane pane, Map<MachineFrame, String> opened, Map<JInternalFrame, String> ofTheDesk) implements Desk {
    static final Map<String, Equipment> OFFERED = Map.of("SpecDrum", new SpecDrumEquipment(),
        "ZX Printer", new PrinterEquipment(), "Kempston Mouse", new MouseEquipment());

    Desktop() {
      this(new JDesktopPane(), new LinkedHashMap<>(), new LinkedHashMap<>());
      pane.setSize(1600, 1200);
    }

    public String nameOf(JInternalFrame window) {
      return opened.containsKey(window) ? opened.get(window) : ofTheDesk.get(window);
    }

    public JInternalFrame window(String name) {
      if (!name.equals("Game Browser")) return equipment(name);
      GamesFrame window = new GamesFrame();
      ofTheDesk.put(window, name);
      pane.add(window);
      window.setVisible(true);
      return window;
    }

    public Map<MachineFrame, String> equipmentOn(EmulatorWindow machine) {
      Map<MachineFrame, String> on = new LinkedHashMap<>();
      opened.keySet().stream().filter(window -> window.isAttached() && window.getMachineWindow() == machine)
          .toList().reversed().forEach(window -> on.put(window, opened.get(window)));
      return on;
    }

    public MachineFrame equipment(String name) {
      MachineFrame window = OFFERED.get(name).open();
      opened.put(window, name);
      pane.add(window);
      window.setVisible(true);
      return window;
    }

    public EmulatorWindow machineFrom(File file) {
      Speccy speccy = machine();
      Snapshots.of(speccy).load(file.getPath());
      Window window = new Window(speccy);
      place(window);
      return window;
    }

    public void place(JInternalFrame window) {
      if (window.getParent() == null) pane.add(window);
      window.setVisible(true);
      window.toFront();
    }

    List<JInternalFrame> backToFront() {
      return List.of(pane.getAllFrames()).reversed();
    }
  }

  /**
   * Done on the event thread, as the desk does it: the resizes a window posts wait until it is done,
   * and are delivered before the next thing asked of that thread.
   */
  static <T> T onTheEventThread(Callable<T> work) throws Exception {
    FutureTask<T> task = new FutureTask<>(work);
    SwingUtilities.invokeAndWait(task);
    return task.get();
  }

  static Speccy machine() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    speccy.control = new Control();
    return speccy;
  }

  static Settings.Configurable settings(Speccy speccy, String name) {
    return speccy.settings().devices().stream().filter(one -> one.device().name().equals(name)).findFirst().orElseThrow();
  }

  /** A 128K with its banks paged, in a window put somewhere, paused, quieter and muted. */
  static Window kept() {
    Speccy speccy = machine();
    Snapshots.of(speccy).load(Fixtures.HERE.resolve("banks.z80").toString());
    Window window = new Window(speccy);
    window.setBounds(40, 30, 520, 410);
    speccy.control.setGeneralOption("pause", true);
    speccy.control.setFilename("/games/banks.tap");
    settings(speccy, "sound.volume").values().set("volume", 37);
    settings(speccy, "sound").values().set("enabled", false);
    return window;
  }

  /**
   * A machine kept paused is paused as soon as its file is read, before it has a window: the desk starts a machine
   * running when it builds it, and one paused only by its window came back some frames on from where it was left.
   */
  @Test
  void aMachineKeptPausedRunsNotAFrameBeforeItIsBack() throws Exception {
    byte[] file = new SzxSession().write(kept());

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.loop.isPaused(), "it runs until its window says it is paused");
  }

  /** And shows what its screen had, as the desk loads it, without a frame having run to paint it. */
  @Test
  void aMachineKeptPausedShowsItsScreenAsSoonAsItIsBack() throws Exception {
    Path file = Files.write(Files.createTempDirectory("session").resolve("0.szx"), new SzxSession().write(kept()));

    Speccy opened = machine();
    opened.picture.active = true;
    Snapshots.of(opened).load(file.toString());
    assertTrue(Arrays.stream(opened.picture.pixels).distinct().count() > 2, "the picture is blank until a frame runs");
  }

  @Test
  void itsMachineAndWhatWasAroundItComeBack() throws Exception {
    Window kept = kept();
    byte[] file = new SzxSession().write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession().bringBack(file, window);

    assertEquals(MachineDescription.of(kept.machine()), MachineDescription.of(opened));
    assertTrue(opened.control.isPaused(), "paused");
    assertEquals("/games/banks.tap", opened.control.getFilename());
    assertEquals(37, settings(opened, "sound.volume").values().get("volume"));
    assertEquals(false, settings(opened, "sound").values().get("enabled"), "muted");
  }

  /** How its picture was shown comes back too: a look of its own, not the one a new window opens with, three times its size. */
  @Test
  void itsPictureIsShownAsItWas() throws Exception {
    Window kept = kept();
    kept.picture().getScreenSettings().apply(Map.of("scaler", "Sharp bilinear", "tv", "Aerial (RF)", "scanlines", "0.3",
        "mask", "SLOT_MASK", "shade", "Green phosphor", "brightness", "1.2", "border", "true"));
    kept.picture().setZoom(3);
    byte[] file = new SzxSession().write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession().bringBack(file, window);

    assertEquals(kept.picture().getScreenSettings().values(), window.picture().getScreenSettings().values());
    assertEquals(3, window.picture().getZoom());
  }

  /** A poke applied to bank 8, with what it overwrote. */
  static PokFile.PokeMod lives() {
    PokFile.PokeMod lives = new PokFile.PokeMod("Infinite lives", "M 8 35136 0 53", "game.pok", "Game");
    lives.getParsedInstruction().setPreviousValue(53);
    lives.getParsedInstruction().setPreviousBank(8);
    lives.getParsedInstruction().setPreviousAddress(35136);
    return lives;
  }

  @Test
  void thePokesItHadAppliedCanStillBeUndone() throws Exception {
    PokesTool pokes = new PokesTool();
    Window kept = kept();
    pokes.applied(kept, List.of(lives()));
    byte[] file = new SzxSession(Set.of(pokes)).write(kept);

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    Window window = new Window(opened);
    new SzxSession(Set.of(pokes)).bringBack(file, window);

    PokFile.PokeMod back = pokes.applied(window).get(0);
    assertEquals(List.of("Infinite lives", "M 8 35136 0 53", "game.pok", "Game"),
        List.of(back.getName(), back.getRawInstruction(), back.getPokFileName(), back.getGameName()));
    assertEquals(List.of(53, 8, 35136), List.of(back.getParsedInstruction().getPreviousValue(),
        back.getParsedInstruction().getPreviousBank(), back.getParsedInstruction().getPreviousAddress()));
  }

  /**
   * A clone no format names is written as the model it copies, and comes back as itself from the
   * block: the header of this file says 48K, and the block a TC2048, which is what it lands on.
   */
  @Test
  void theMachineTheBlockNamesIsTheOneTheFileLandsOn() throws Exception {
    Speccy kept = machine();
    String timex = kept.machine.model(Tc2048.class).shortName();
    byte[] file = new SzxFormat().write(kept, Map.of("OOZX", ("machine=" + timex).getBytes()), note -> { });

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });

    assertEquals(Tc2048.class, opened.machine.current.getClass());
  }

  @Test
  void romsThatAreNotHereLeaveItOnItsOwnAndSaySo() throws Exception {
    Speccy kept = machine();
    kept.machine.select(kept.machine.model(SpecPlus3.class));
    kept.roms.chooseSet(kept.machine.current, "Version 4.1");
    List<String> notHere = kept.roms.running(kept.machine.current);
    byte[] file = new SzxSession().write(new Window(kept));

    Speccy opened = machine();
    List<String> notes = new java.util.ArrayList<>();
    new SzxFormat().read(file, opened, notes::add);

    assertEquals(SpecPlus3.class, opened.machine.current.getClass());
    assertEquals("Version 4.0", opened.roms.chosenSet(opened.machine.current));
    assertTrue(notes.stream().anyMatch(note -> note.contains(notHere.get(0))), String.valueOf(notes));
  }

  /**
   * What is plugged into it is in before it is read, whether its window plugged it or a setting did:
   * plugging a SpecDrum in resets the machine, which once it is read would undo the reading, and a
   * Melodik switched on afterwards has missed its registers in the AY block.
   */
  @Test
  void whatIsPluggedIntoItIsInBeforeItIsRead() throws Exception {
    Speccy kept = machine();
    kept.peripheralRegistry.allThatAre(SpecDrumPeripheral.class).get(0).plugIn(true);
    settings(kept, "melodik").values().set("fitted", true);
    if (kept.peripheralRegistry.update()) kept.machine.reset(true);
    byte[] page = new byte[16384];
    Arrays.fill(page, (byte) 0xA5);
    CoreFields.PAGE.set(kept.banks, 2, page);
    kept.peripheralRegistry.allThatAre(MelodikPeripheral.class).get(0).register(7, 0x38);
    byte[] file = new SzxSession().write(new Window(kept));

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(SpecDrumPeripheral.class), "the SpecDrum is not in");
    assertTrue(opened.peripheralRegistry.isActive(MelodikPeripheral.class), "the Melodik is not in");
    assertEquals(0x38, opened.peripheralRegistry.allThatAre(MelodikPeripheral.class).get(0).register(7));
    assertEquals(MachineDescription.of(kept), MachineDescription.of(opened));
    assertFalse(opened.peripheralRegistry.update(), "clipping their windows on now would reset it");
  }

  /**
   * A machine on ROMs of its own comes back on them, from where they are, and what new machines start with stays
   * as it was: a machine's ROMs are its own, as its settings are.
   */
  @Test
  void theRomsAMachineRunsOnComeBackWithItAndNoOtherTakesThem() throws Exception {
    Speccy kept = machine();
    Path own = Files.write(Files.createTempDirectory("roms").resolve("own.rom"), kept.roms.of(kept.machine.current, 0, 0x4000));
    List<String> newOnes = Configuration.shared().of(RomFiles.class).running(kept.machine.current);
    kept.roms.runOn(kept.machine.current, List.of(own.toString()));
    byte[] file = new SzxSession().write(new Window(kept));

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    assertEquals(List.of(own.toString()), opened.roms.running(opened.machine.current));
    assertEquals(newOnes, Configuration.shared().of(RomFiles.class).running(opened.machine.current), "what new machines start on");
  }

  static Stream<Class<? extends IdeInterface>> boardsWithDisks() {
    return Stream.of(DivIdePeripheral.class, DivMmcPeripheral.class, ZxataspPeripheral.class, ZxcfPeripheral.class,
        SimpleIdePeripheral.class, ZxmmcPeripheral.class);
  }

  /**
   * The disks of an IDE or MMC interface, which no block of SZX has, go by their file as a setting of the
   * board, as Fuse keeps them: the machine comes back with each in its unit.
   */
  @ParameterizedTest
  @MethodSource("boardsWithDisks")
  void theDisksOfABoardComeBackByTheirFile(Class<? extends IdeInterface> type) throws Exception {
    File image = Files.createTempDirectory("disks").resolve("work.hdf").toFile();
    IdeChannel.createHdf(image, 64);
    Speccy kept = machine();
    IdeInterface board = type.cast(kept.peripheralRegistry.find(type.asSubclass(Peripheral.class)));
    ((Pluggable) board).plugIn(true);
    if (kept.peripheralRegistry.update()) kept.machine.reset(true);
    board.insert(board.units() - 1, image);
    byte[] file = new SzxSession().write(new Window(kept));

    Speccy opened = machine();
    new SzxFormat().read(file, opened, note -> { });
    assertEquals(disksOf(board), disksOf(type.cast(opened.peripheralRegistry.find(type.asSubclass(Peripheral.class)))));
  }

  /** The file of the image in each unit, null where there is none. */
  private static List<String> disksOf(IdeInterface board) {
    List<String> disks = new ArrayList<>();
    for (int unit = 0; unit < board.units(); unit++) disks.add(board.drive(unit).present() ? board.drive(unit).filename() : null);
    return disks;
  }

  /**
   * What new machines start with, changed while this one is open, is for the machines opened after
   * it: this one keeps the joystick it had, and comes back with it rather than with the new one.
   */
  @Test
  void theDefaultsChangedWhileItIsOpenAreForTheMachinesOpenedAfterIt() throws Exception {
    Speccy kept = machine();
    Settings.Values defaults = Settings.defaults().stream().filter(one -> one.device().name().equals("input"))
        .findFirst().orElseThrow().values();
    Object was = defaults.get("joystick1");
    Input.Setup.Pad sinclair = new Input.Setup.Pad();
    sinclair.output = Joystick.JoystickType.JOYSTICK_TYPE_SINCLAIR_1;
    try {
      defaults.set("joystick1", sinclair);
      assertNull(Input.of(kept).setup.joystick1.output, "the open one keeps its own");

      byte[] file = new SzxSession().write(new Window(kept));
      Speccy opened = machine();
      assertEquals(sinclair.output, Input.of(opened).setup.joystick1.output, "a new one starts with it");
      new SzxFormat().read(file, opened, note -> { });

      assertNull(Input.of(opened).setup.joystick1.output, "the kept one comes back with its own");
    } finally {
      defaults.set("joystick1", was);
    }
  }

  /**
   * The other windows of the desk: one of equipment comes back folded as it was, and the game
   * browser looking for what it was. One clipped onto a machine goes with the machine, and one the
   * desk did not open is not kept.
   */
  @Test
  void theOtherWindowsOfTheDeskComeBackAsTheyWereLeft() throws Exception {
    try {
      Desktop desk = new Desktop();
      GamesFrame.Search looking = new GamesFrame.Search("dizzy", "On this machine", "Most known",
          new KnowsTheGames.Slice(1987, "Arcade: Platform", "ZX-Spectrum 48K"), true, false, false, true, false);
      List<Map<String, String>> kept = onTheEventThread(() -> {
        Desk.isRunBy(desk);
        desk.equipment("ZX Printer").setCompact(false, 230);
        ((GamesFrame) desk.window("Game Browser")).search(looking);
        Window machine = kept();
        desk.pane().add(machine);
        desk.equipment("SpecDrum").attachTo(machine, AttachedFrame.Dock.BOTTOM);
        desk.pane().add(new JInternalFrame("Loose"));
        SzxSession session = new SzxSession();
        return Stream.of(desk.pane().getAllFrames()).map(session::keep).filter(inside -> !inside.isEmpty()).toList();
      });
      assertEquals(2, kept.size(), "the printer and the browser: " + kept);

      Desktop another = new Desktop();
      List<JInternalFrame> back = onTheEventThread(() -> {
        Desk.isRunBy(another);
        SzxSession session = new SzxSession();
        return kept.stream().map(session::bringBack).toList();
      });
      MachineFrame printer = back.stream().filter(MachineFrame.class::isInstance).map(MachineFrame.class::cast).findFirst().orElseThrow();
      assertEquals("ZX Printer expanded to 230", another.opened().get(printer)
          + (printer.isCompact() ? " compact" : " expanded") + " to " + printer.expandedHeight());
      GamesFrame games = back.stream().filter(GamesFrame.class::isInstance).map(GamesFrame.class::cast).findFirst().orElseThrow();
      assertEquals(looking, onTheEventThread(games::search));
    } finally {
      Desk.isRunBy(null);
    }
  }

  /**
   * The whole desk exactly as it was left: three machines, each with what is plugged into it, one with
   * a cartridge and a tape in and another with a disk, equipment clipped onto two of them, the game browser minimised, a machine
   * maximised and a printer on its own. Kept as the desk keeps them and brought back onto another
   * desk, every window is where it was, as big, in the same state and stacked the same way; kept
   * again, each machine is the same file byte for byte.
   */
  @Test
  void theDeskComesBackExactlyAsItWasLeft(@TempDir Path folder) throws Exception {
    try {
      PokesTool pokes = new PokesTool();
      SzxSession session = new SzxSession(Set.of(pokes));
      Desktop desk = new Desktop();
      onTheEventThread(() -> {
        Desk.isRunBy(desk);
        Window banks = kept();
        desk.place(banks);
        banks.picture().getScreenSettings().apply(Map.of("scaler", "Sharp bilinear", "scanlines", "0.3", "border", "true"));
        banks.picture().setZoom(3);
        pokes.applied(banks, List.of(lives()));
        desk.equipment("SpecDrum").attachTo(banks, AttachedFrame.Dock.BOTTOM);
        MachineFrame printer = desk.equipment("ZX Printer");
        printer.setCompact(false, 230);
        printer.attachTo(banks, AttachedFrame.Dock.BOTTOM);

        Speccy fortyEight = machine();
        settings(fortyEight, "melodik").values().set("fitted", true);
        Input.Setup.Pad sinclair = new Input.Setup.Pad();
        sinclair.output = Joystick.JoystickType.JOYSTICK_TYPE_SINCLAIR_1;
        settings(fortyEight, "input").values().set("joystick1", sinclair);
        WhatIsPluggedInComesBackTest.plugged(fortyEight, Interface2Peripheral.class)
            .inserted(new Cartridge("Jetpac", WhatIsPluggedInComesBackTest.pattern(70, Cartridge.SIZE), null));
        fortyEight.peripheralRegistry.allThatAre(MelodikPeripheral.class).get(0).register(7, 0x38);
        Tape.of(fortyEight).insert(Files.write(folder.resolve("jetpac.tap"), new byte[]{3, 0, 0, 1, 2, 2, 0, (byte) 0xff, 7}).toFile());
        Tape.of(fortyEight).setSelectedBlock(1);
        Window jetpac = new Window(fortyEight);
        desk.place(jetpac);
        jetpac.setBounds(600, 40, 380, 300);
        jetpac.picture().getScreenSettings().apply(Map.of("tv", "Aerial (RF)", "shade", "Green phosphor"));
        MachineFrame mouse = desk.equipment("Kempston Mouse");
        mouse.setSize(300, 200);
        mouse.attachTo(jetpac, AttachedFrame.Dock.RIGHT);

        GamesFrame games = (GamesFrame) desk.window("Game Browser");
        games.search(new GamesFrame.Search("dizzy", "On this machine", "Most known",
            new KnowsTheGames.Slice(1987, "Arcade: Platform", "ZX-Spectrum 48K"), true, false, false, true, false));
        games.setBounds(100, 500, 640, 420);
        games.setIcon(true);

        Speccy pentagon = machine();
        pentagon.machine.select(pentagon.machine.model(Pentagon.class));
        Beta128Peripheral beta = (Beta128Peripheral) pentagon.peripheralRegistry.find(Beta128Peripheral.class);
        beta.system(0x3d);
        beta.drive(0).insert(Disk.openBuffer("game.trd", WhatIsPluggedInComesBackTest.trd(5)), false);
        beta.drive(0).disk.write(folder.resolve("game.trd").toFile());
        WhatIsPluggedInComesBackTest.plugged(pentagon, CovoxPeripheral.class).written(0xc0);
        Window big = new Window(pentagon);
        desk.place(big);
        big.setBounds(300, 200, 500, 400);
        big.setMaximum(true);

        MachineFrame alone = desk.equipment("ZX Printer");
        alone.setCompact(false, 180);
        alone.setBounds(1000, 600, 260, 300);
        return null;
      });
      List<OOZxConfiguration.WindowState> kept = onTheEventThread(() -> desk.keep(session, desk.backToFront(), folder.resolve("left").toFile()));
      List<String> left = onTheEventThread(() -> seen(desk));

      Desktop another = new Desktop();
      onTheEventThread(() -> {
        Desk.isRunBy(another);
        another.bringBack(session, kept);
        return null;
      });
      List<OOZxConfiguration.WindowState> again = onTheEventThread(() -> another.keep(session, another.backToFront(), folder.resolve("again").toFile()));

      assertEquals(left, onTheEventThread(() -> seen(another)));
      assertTrue(described(kept).stream().anyMatch(line -> line.startsWith("TAPE ")), "the tape in the 48K's deck is not kept");
      assertTrue(described(kept).stream().anyMatch(line -> line.startsWith("BDSK ")), "the disk in the Pentagon's drive is not kept");
      assertEquals(described(kept), described(again));
      for (int at = 0; at < kept.size(); at++) {
        if (kept.get(at).getFilePath() != null) {
          assertArrayEquals(Files.readAllBytes(Path.of(kept.get(at).getFilePath())), Files.readAllBytes(Path.of(again.get(at).getFilePath())));
        }
      }
    } finally {
      Desk.isRunBy(null);
    }
  }

  /** Every window from the back to the front: which, where, how big, whether maximised or minimised, how it folds and what it is clipped onto. */
  private static List<String> seen(Desktop desk) {
    List<JInternalFrame> windows = desk.backToFront();
    return windows.stream().map(window -> (window instanceof Window machine ? machine.machine().machine.current.getName() : desk.nameOf(window))
        + " " + window.getNormalBounds() + (window.isMaximum() ? " maximised" : "") + (window.isIcon() ? " minimised" : "")
        + (window instanceof AttachedFrame folding ? (folding.isCompact() ? " compact" : " expanded") + " to " + folding.expandedHeight() : "")
        + (window instanceof AttachedFrame clipped && clipped.isAttached() ? " on the " + clipped.dockedTo() + " of " + windows.indexOf(clipped.getMachineWindow()) : ""))
        .toList();
  }

  /** What the desk wrote down of each window: where it is, and a machine by the blocks of its file and any other by what it has inside. */
  private static List<String> described(List<OOZxConfiguration.WindowState> kept) throws Exception {
    List<String> said = new ArrayList<>();
    for (OOZxConfiguration.WindowState state : kept) {
      said.add(state.getType() + " " + new Rectangle(state.getX(), state.getY(), state.getWidth(), state.getHeight())
          + (state.isMaximum() ? " maximised" : "") + (state.isIcon() ? " minimised" : "") + " " + state.getInside());
      if (state.getFilePath() != null) said.addAll(blocks(Files.readAllBytes(Path.of(state.getFilePath()))));
    }
    return said;
  }

  /** An SZX block by block: its header, what is around the machine a line each, and the rest by their digest. */
  static List<String> blocks(byte[] szx) {
    List<String> said = new ArrayList<>(List.of(HexFormat.of().formatHex(szx, 0, 8)));
    ByteBuffer in = ByteBuffer.wrap(szx, 8, szx.length - 8).order(ByteOrder.LITTLE_ENDIAN);
    while (in.remaining() >= 8) {
      byte[] id = new byte[4];
      in.get(id);
      byte[] payload = new byte[in.getInt()];
      in.get(payload);
      String block = new String(id, StandardCharsets.ISO_8859_1);
      if (block.equals("OOZX")) new String(payload, StandardCharsets.UTF_8).lines().forEach(line -> said.add(block + " " + line));
      else said.add(block + " " + Fields.digest(payload));
    }
    return said;
  }

  @Test
  void anotherEmulatorReadsTheFilePassingOverWhatIsAroundTheMachine() throws Exception {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertFalse(Reference.read(new SzxSession().write(kept()), "szx").isRefused(), "libspectrum refused it");
  }
}

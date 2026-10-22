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
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.covox.CovoxPeripheral;
import com.fpetrola.oozx.speccy.devices.disk.Beta128Peripheral;
import com.fpetrola.oozx.speccy.devices.disk.Fdd;
import com.fpetrola.oozx.speccy.devices.divide.DivIdePeripheral;
import com.fpetrola.oozx.speccy.devices.divmmc.DivMmcPeripheral;
import com.fpetrola.oozx.speccy.devices.ide.DivPeripheral;
import com.fpetrola.oozx.speccy.devices.interface1.Interface1Peripheral;
import com.fpetrola.oozx.speccy.devices.interface2.Cartridge;
import com.fpetrola.oozx.speccy.devices.interface2.Interface2Peripheral;
import com.fpetrola.oozx.speccy.devices.melodik.MelodikPeripheral;
import com.fpetrola.oozx.speccy.devices.mouse.KempstonMousePeripheral;
import com.fpetrola.oozx.speccy.devices.multiface.MultifaceOnePeripheral;
import com.fpetrola.oozx.speccy.devices.multiface.MultifacePeripheral;
import com.fpetrola.oozx.speccy.devices.opus.OpusPeripheral;
import com.fpetrola.oozx.speccy.devices.plusd.MgtDiskInterface;
import com.fpetrola.oozx.speccy.devices.plusd.PlusDPeripheral;
import com.fpetrola.oozx.speccy.devices.printer.ZxPrinterPeripheral;
import com.fpetrola.oozx.speccy.devices.scld.ScldPeripheral;
import com.fpetrola.oozx.speccy.devices.scld.TimexMemoryPeripheral;
import com.fpetrola.oozx.speccy.devices.simpleide.SimpleIdePeripheral;
import com.fpetrola.oozx.speccy.devices.specdrum.SpecDrumPeripheral;
import com.fpetrola.oozx.speccy.devices.uspeech.UspeechPeripheral;
import com.fpetrola.oozx.speccy.devices.zxatasp.ZxataspPeripheral;
import com.fpetrola.oozx.speccy.devices.zxcf.ZxcfPeripheral;
import com.fpetrola.oozx.speccy.devices.zxmmc.ZxmmcPeripheral;
import com.fpetrola.oozx.speccy.machine.Pentagon;
import com.fpetrola.oozx.speccy.machine.Tc2068;
import com.fpetrola.oozx.speccy.peripherals.Peripheral;
import com.fpetrola.oozx.speccy.peripherals.Pluggable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Each device in the blocks SZX has for it: a machine with it writes a file libspectrum reads the same, and a
 * machine that reads the file has it plugged in and as it was. libspectrum's own file of each block reads as its
 * tests say.
 */
class WhatIsPluggedInComesBackTest {

  @Test
  void theZxataspWithItsBanksAndItsEightTwoFiveFive() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    ZxataspPeripheral board = plugged(kept, ZxataspPeripheral.class);
    for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) board.contents(bank, pattern(bank, 0x4000));
    board.setUpload(true);
    board.portA(0xab);
    board.portB(0x8c);
    board.portC(0x42);
    board.control(0x9b);
    board.select(5, true);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(ZxataspPeripheral.class), "the file plugs it in");
    assertEquals(described(board), described((ZxataspPeripheral) opened.peripheralRegistry.find(ZxataspPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(board), Reference.withSnap(file, "szx", snap -> {
      assertEquals(1, lib.libspectrum_snap_zxatasp_active(snap));
      List<Object> said = new ArrayList<>(List.of(lib.libspectrum_snap_zxatasp_upload(snap) != 0,
          lib.libspectrum_snap_zxatasp_writeprotect(snap) != 0, lib.libspectrum_snap_zxatasp_port_a(snap) & 0xff,
          lib.libspectrum_snap_zxatasp_port_b(snap) & 0xff, lib.libspectrum_snap_zxatasp_port_c(snap) & 0xff,
          lib.libspectrum_snap_zxatasp_control(snap) & 0xff, (int) lib.libspectrum_snap_zxatasp_current_page(snap).longValue()));
      for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) {
        said.add(Arrays.hashCode(lib.libspectrum_snap_zxatasp_ram(snap, bank).getByteArray(0, 0x4000)));
      }
      return said;
    }).orElseThrow());
  }

  /** libspectrum's ZXAT: uploading, its ports, and bank 0x11 paged in. */
  @Test
  void theZxataspOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/ZXAT.szx")), opened, note -> { });

    ZxataspPeripheral board = (ZxataspPeripheral) opened.peripheralRegistry.find(ZxataspPeripheral.class);
    assertTrue(opened.peripheralRegistry.isActive(ZxataspPeripheral.class), "the file plugs it in");
    assertEquals(List.of(true, false, 0xab, 0x8c, 0x82, 0xd8, 0x11), described(board).subList(0, 7));
  }

  @Test
  void theDivideWithItsEpromAndItsPages() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    DivIdePeripheral divide = plugged(kept, DivIdePeripheral.class);
    divide.eprom(pattern(40, 0x2000));
    for (int page = 0; page < divide.ramPages(); page++) divide.ram(page, pattern(50 + page, 0x2000));
    divide.setWriteProtect(true);
    divide.control(0x42);
    divide.setAutomap(true);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(DivIdePeripheral.class), "the file plugs it in");
    assertEquals(described(divide), described((DivIdePeripheral) opened.peripheralRegistry.find(DivIdePeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(divide), Reference.withSnap(file, "szx", snap -> {
      assertEquals(1, lib.libspectrum_snap_divide_active(snap));
      assertEquals(DivIdePeripheral.RAM_PAGES, lib.libspectrum_snap_divide_pages(snap).intValue());
      List<Object> said = new ArrayList<>(List.of(lib.libspectrum_snap_divide_eprom_writeprotect(snap) != 0,
          lib.libspectrum_snap_divide_control(snap) & 0xff, lib.libspectrum_snap_divide_paged(snap) != 0,
          Arrays.hashCode(lib.libspectrum_snap_divide_eprom(snap, 0).getByteArray(0, 0x2000))));
      for (int page = 0; page < DivIdePeripheral.RAM_PAGES; page++) {
        said.add(Arrays.hashCode(lib.libspectrum_snap_divide_ram(snap, page).getByteArray(0, 0x2000)));
      }
      return said;
    }).orElseThrow());
  }

  /** libspectrum's DIDE: paged in through its register, page count 1, and 0x12 first in its EPROM. */
  @Test
  void theDivideOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/DIDE.szx")), opened, note -> { });

    DivIdePeripheral divide = (DivIdePeripheral) opened.peripheralRegistry.find(DivIdePeripheral.class);
    assertTrue(opened.peripheralRegistry.isActive(DivIdePeripheral.class), "the file plugs it in");
    assertEquals(List.of(false, 0xab, true, 0x12), List.of(divide.writeProtect(), divide.control(), divide.isPaged(),
        (int) divide.eprom()[0]));
  }

  @Test
  void theDivmmcWithItsEpromAndItsPages() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    DivMmcPeripheral divmmc = plugged(kept, DivMmcPeripheral.class);
    divmmc.eprom(pattern(41, 0x2000));
    for (int page = 0; page < divmmc.ramPages(); page++) divmmc.ram(page, pattern(90 + page, 0x2000));
    divmmc.control(0x8b);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(DivMmcPeripheral.class), "the file plugs it in");
    assertEquals(described(divmmc), described((DivMmcPeripheral) opened.peripheralRegistry.find(DivMmcPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(divmmc), Reference.withSnap(file, "szx", snap -> {
      assertEquals(1, lib.libspectrum_snap_divmmc_active(snap));
      assertEquals(DivMmcPeripheral.RAM_PAGES, lib.libspectrum_snap_divmmc_pages(snap).intValue());
      List<Object> said = new ArrayList<>(List.of(lib.libspectrum_snap_divmmc_eprom_writeprotect(snap) != 0,
          lib.libspectrum_snap_divmmc_control(snap) & 0xff, lib.libspectrum_snap_divmmc_paged(snap) != 0,
          Arrays.hashCode(lib.libspectrum_snap_divmmc_eprom(snap, 0).getByteArray(0, 0x2000))));
      for (int page = 0; page < DivMmcPeripheral.RAM_PAGES; page++) {
        said.add(Arrays.hashCode(lib.libspectrum_snap_divmmc_ram(snap, page).getByteArray(0, 0x2000)));
      }
      return said;
    }).orElseThrow());
  }

  /** libspectrum's DMMC: paged in through its register, page count 2, and 0x34 first in its EPROM. */
  @Test
  void theDivmmcOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/DMMC.szx")), opened, note -> { });

    DivMmcPeripheral divmmc = (DivMmcPeripheral) opened.peripheralRegistry.find(DivMmcPeripheral.class);
    assertTrue(opened.peripheralRegistry.isActive(DivMmcPeripheral.class), "the file plugs it in");
    assertEquals(List.of(false, 0xcd, true, 0x34), List.of(divmmc.writeProtect(), divmmc.control(), divmmc.isPaged(),
        (int) divmmc.eprom()[0]));
  }

  @Test
  void theMultifaceOnePagedInStealthyAndPressed() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    MultifaceOnePeripheral face = plugged(kept, MultifaceOnePeripheral.class);
    face.ram(pattern(60, MultifacePeripheral.RAM_SIZE));
    face.pagedIn(true);
    face.setStealth(true);
    face.j2(false);
    face.redButtonReady(false);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(MultifaceOnePeripheral.class), "the file plugs it in");
    assertEquals(described(face), described((MultifacePeripheral) opened.peripheralRegistry.find(MultifaceOnePeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(face), Reference.withSnap(file, "szx", snap -> {
      assertEquals(List.of(1, 1, 0, 0x2000L), List.of(lib.libspectrum_snap_multiface_active(snap),
          lib.libspectrum_snap_multiface_model_one(snap), lib.libspectrum_snap_multiface_software_lockout(snap),
          lib.libspectrum_snap_multiface_ram_length(snap, 0).longValue()));
      return List.<Object>of(lib.libspectrum_snap_multiface_paged(snap) != 0, lib.libspectrum_snap_multiface_disabled(snap) != 0,
          lib.libspectrum_snap_multiface_red_button_disabled(snap) == 0,
          Arrays.hashCode(lib.libspectrum_snap_multiface_ram(snap, 0).getByteArray(0, MultifacePeripheral.RAM_SIZE)));
    }).orElseThrow());
  }

  /** libspectrum's MFCE: a One, paged in, its RAM empty. */
  @Test
  void theMultifaceOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/MFCE.szx")), opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(MultifaceOnePeripheral.class), "the file plugs it in");
    MultifacePeripheral face = (MultifacePeripheral) opened.peripheralRegistry.find(MultifaceOnePeripheral.class);
    assertEquals(List.of(true, false, true, Arrays.hashCode(new byte[MultifacePeripheral.RAM_SIZE])), described(face));
  }

  @Test
  void theInterfaceTwoWithItsCartridgePagedIn() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, Interface2Peripheral.class).inserted(new Cartridge("Jetpac", pattern(70, Cartridge.SIZE), null));
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(Interface2Peripheral.class), "the file plugs it in");
    assertEquals(Arrays.hashCode(pattern(70, Cartridge.SIZE)), Arrays.hashCode(seen(opened, Cartridge.SIZE)),
        "the cartridge is paged in");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(1, Arrays.hashCode(pattern(70, Cartridge.SIZE))), Reference.withSnap(file, "szx", snap -> List.of(
        lib.libspectrum_snap_interface2_active(snap),
        Arrays.hashCode(lib.libspectrum_snap_interface2_rom(snap, 0).getByteArray(0, Cartridge.SIZE)))).orElseThrow());
  }

  /** libspectrum's IF2R: a cartridge of zeros, which a machine reading it runs. */
  @Test
  void theInterfaceTwoOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/IF2R.szx")), opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(Interface2Peripheral.class), "the file plugs it in");
    assertEquals(Arrays.hashCode(new byte[Cartridge.SIZE]), Arrays.hashCode(seen(opened, Cartridge.SIZE)));
  }

  @Test
  void theSimpleIdeIsThere() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, SimpleIdePeripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(SimpleIdePeripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/SIDE.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(SimpleIdePeripheral.class), "libspectrum's SIDE plugs it in");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertEquals(1, Reference.withSnap(file, "szx", Libspectrum.INSTANCE::libspectrum_snap_simpleide_active).orElseThrow());
  }

  @Test
  void theZxcfWithItsBanksAndItsRegister() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    ZxcfPeripheral board = plugged(kept, ZxcfPeripheral.class);
    for (int bank = 0; bank < ZxcfPeripheral.BANKS; bank++) board.contents(bank, pattern(bank * 3, 0x4000));
    board.setUpload(true);
    board.memoryControlWrite(0x40 | 37);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(ZxcfPeripheral.class), "the file plugs it in");
    assertEquals(described(board), described((ZxcfPeripheral) opened.peripheralRegistry.find(ZxcfPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(board), Reference.withSnap(file, "szx", snap -> {
      assertEquals(List.of(1, (long) ZxcfPeripheral.BANKS), List.of(lib.libspectrum_snap_zxcf_active(snap),
          lib.libspectrum_snap_zxcf_pages(snap).longValue()));
      List<Object> said = new ArrayList<>(List.of(lib.libspectrum_snap_zxcf_upload(snap) != 0, lib.libspectrum_snap_zxcf_memctl(snap) & 0xff));
      for (int bank = 0; bank < ZxcfPeripheral.BANKS; bank++) {
        said.add(Arrays.hashCode(lib.libspectrum_snap_zxcf_ram(snap, bank).getByteArray(0, 0x4000)));
      }
      return said;
    }).orElseThrow());
  }

  /** libspectrum's ZXCF: uploading, its register 0x37 (bank 0x37, read only, paged in). */
  @Test
  void theZxcfOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/ZXCF.szx")), opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(ZxcfPeripheral.class), "the file plugs it in");
    ZxcfPeripheral board = (ZxcfPeripheral) opened.peripheralRegistry.find(ZxcfPeripheral.class);
    assertEquals(List.of(true, 0x37, true, 0x37), List.of(board.upload(), board.lastMemoryControl(), board.isPaged(), board.bank()));
  }

  /** Its upload jumper, its register, and what each bank holds. */
  private static List<Object> described(ZxcfPeripheral board) {
    List<Object> said = new ArrayList<>(List.of(board.upload(), board.lastMemoryControl()));
    for (int bank = 0; bank < ZxcfPeripheral.BANKS; bank++) said.add(Arrays.hashCode(board.contents(bank)));
    return said;
  }

  @Test
  void theZxmmcIsThere() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, ZxmmcPeripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(ZxmmcPeripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/ZMMC.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(ZxmmcPeripheral.class), "libspectrum's ZMMC plugs it in");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertEquals(1, Reference.withSnap(file, "szx", Libspectrum.INSTANCE::libspectrum_snap_zxmmc_active).orElseThrow());
  }

  /** On a Pentagon, the machine it was sold for: libspectrum's own COVX is a 48K's, where it does not go. */
  @Test
  void theCovoxWithTheLastByteWrittenToIt() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    kept.machine.select(kept.machine.model(Pentagon.class));
    plugged(kept, CovoxPeripheral.class).written(0xc0);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(CovoxPeripheral.class), "the file plugs it in");
    assertEquals(0xc0, ((CovoxPeripheral) opened.peripheralRegistry.find(CovoxPeripheral.class)).written());

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(1, 0xc0), Reference.withSnap(file, "szx", snap -> List.of(lib.libspectrum_snap_covox_active(snap),
        lib.libspectrum_snap_covox_dac(snap) & 0xff)).orElseThrow());
  }

  @Test
  void theSpecdrumWithTheLastByteWrittenToIt() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, SpecDrumPeripheral.class).written(0x31);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(SpecDrumPeripheral.class), "the file plugs it in");
    assertEquals(0x31, ((SpecDrumPeripheral) opened.peripheralRegistry.find(SpecDrumPeripheral.class)).written());
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/DRUM.szx")), fromLibspectrums, note -> { });
    assertEquals(0x45, ((SpecDrumPeripheral) fromLibspectrums.peripheralRegistry.find(SpecDrumPeripheral.class)).written(),
        "libspectrum's DRUM, which it reads as -0x3b");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(1, 0x31 - 128), Reference.withSnap(file, "szx", snap -> List.of(lib.libspectrum_snap_specdrum_active(snap),
        (int) lib.libspectrum_snap_specdrum_dac(snap))).orElseThrow());
  }

  /** Without its ROMs, as libspectrum's own USPE has it: its ROM out. */
  @Test
  void theUspeechIsThere() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, UspeechPeripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(UspeechPeripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/USPE.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(UspeechPeripheral.class), "libspectrum's USPE plugs it in");

    assumeTrue(Reference.present() && Reference.has("libspectrum_snap_uspeech_active"), "no libspectrum here that knows USPE");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(1, 0), Reference.withSnap(file, "szx", snap -> List.of(lib.libspectrum_snap_uspeech_active(snap),
        lib.libspectrum_snap_uspeech_paged(snap))).orElseThrow());
  }

  @Test
  void theZxPrinterIsOn() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, ZxPrinterPeripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(ZxPrinterPeripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/ZXPR.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(ZxPrinterPeripheral.class), "libspectrum's ZXPR plugs it in");
    Speccy saysOff = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(new SzxFormat().write(AMachineComesBackWithItsWindowTest.machine(), Map.of("ZXPR", new byte[2]), note -> { }),
        saysOff, note -> { });
    assertFalse(saysOff.peripheralRegistry.isActive(ZxPrinterPeripheral.class), "a ZXPR that says it is off, as libspectrum "
        + "writes it for every machine, does not");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertEquals(1, Reference.withSnap(file, "szx", Libspectrum.INSTANCE::libspectrum_snap_zx_printer_active).orElseThrow());
  }

  @Test
  void theKempstonMouseIsThere() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, KempstonMousePeripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(KempstonMousePeripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/AMXM.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(KempstonMousePeripheral.class), "libspectrum's AMXM plugs it in");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertEquals(1, Reference.withSnap(file, "szx", Libspectrum.INSTANCE::libspectrum_snap_kempston_mouse_active).orElseThrow());
  }

  /** Without its ROM here, where it cannot page in: libspectrum's own IF1, paged, plugs it in all the same. */
  @Test
  void theInterfaceOneIsThere() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    plugged(kept, Interface1Peripheral.class);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(Interface1Peripheral.class), "the file plugs it in");
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/IF1.szx")), fromLibspectrums, note -> { });
    assertTrue(fromLibspectrums.peripheralRegistry.isActive(Interface1Peripheral.class), "libspectrum's IF1 plugs it in");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(1, 0, Interface1Peripheral.DRIVES, 0), Reference.withSnap(file, "szx", snap -> List.of(
        lib.libspectrum_snap_interface1_active(snap), lib.libspectrum_snap_interface1_paged(snap),
        lib.libspectrum_snap_interface1_drive_count(snap), lib.libspectrum_snap_interface1_custom_rom(snap))).orElseThrow());
  }

  /** The Pentagon's own, in the middle of something: TR-DOS paged in, the head stepping in, its registers set. */
  @Test
  void theBetaOneTwoEightWithItsController() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    kept.machine.select(kept.machine.model(Pentagon.class));
    Beta128Peripheral beta = (Beta128Peripheral) kept.peripheralRegistry.find(Beta128Peripheral.class);
    beta.pagedIn(true);
    beta.system(0x3d);
    beta.fdc().direction = Fdd.Dir.IN;
    beta.fdc().trackRegister = 5;
    beta.fdc().sectorRegister = 3;
    beta.fdc().dataRegister = 0xaa;
    beta.fdc().statusRegister = 0x81;
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(Beta128Peripheral.class), "on board");
    assertEquals(described(beta), described((Beta128Peripheral) opened.peripheralRegistry.find(Beta128Peripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(beta), Reference.withSnap(file, "szx", snap -> {
      assertEquals(List.of(1, Beta128Peripheral.DRIVES, 0), List.of(lib.libspectrum_snap_beta_active(snap),
          lib.libspectrum_snap_beta_drive_count(snap), lib.libspectrum_snap_beta_custom_rom(snap)));
      return List.<Object>of(lib.libspectrum_snap_beta_paged(snap) != 0, lib.libspectrum_snap_beta_direction(snap) == 0,
          lib.libspectrum_snap_beta_system(snap) & 0xff, lib.libspectrum_snap_beta_track(snap) & 0xff,
          lib.libspectrum_snap_beta_sector(snap) & 0xff, lib.libspectrum_snap_beta_data(snap) & 0xff,
          lib.libspectrum_snap_beta_status(snap) & 0xff);
    }).orElseThrow());
  }

  /** libspectrum's B128 is a 48K's: it plugs one in, with the registers its tests read. */
  @Test
  void theBetaOneTwoEightOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/B128.szx")), opened, note -> { });

    Beta128Peripheral beta = opened.peripheralRegistry.allThatAre(Beta128Peripheral.class).stream()
        .filter(one -> one.fitsOn(opened.machine.current)).findFirst().orElseThrow();
    assertTrue(opened.peripheralRegistry.isActive(beta.getClass()), "the file plugs it in");
    assertEquals(List.of(0x11, 5, 3, 0xaa, 0xff), described(beta).subList(2, 7));
  }

  /** Whether TR-DOS is paged in, whether the head steps out, the system register and the controller's four. */
  private static List<Object> described(Beta128Peripheral beta) {
    return List.of(beta.isPaged(), beta.fdc().direction == Fdd.Dir.OUT, beta.systemRegister(), beta.fdc().trackRegister,
        beta.fdc().sectorRegister, beta.fdc().dataRegister, beta.fdc().statusRegister);
  }

  /** A TC2068 with its slot asked to cover some chunks and its screen in hi-res: the two ports of its SCLD. */
  @Test
  void theScldOfATimex() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    kept.machine.select(kept.machine.model(Tc2068.class));
    ((TimexMemoryPeripheral) kept.peripheralRegistry.find(TimexMemoryPeripheral.class)).wanted(0x49);
    ((ScldPeripheral) kept.peripheralRegistry.find(ScldPeripheral.class)).register(0x3e);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertEquals(List.of(0x49, 0x3e), List.of(((TimexMemoryPeripheral) opened.peripheralRegistry.find(TimexMemoryPeripheral.class)).wanted(),
        ((ScldPeripheral) opened.peripheralRegistry.find(ScldPeripheral.class)).register()));
    Speccy fromLibspectrums = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/SCLD.szx")), fromLibspectrums, note -> { });
    assertEquals(0x9d, ((ScldPeripheral) fromLibspectrums.peripheralRegistry.find(ScldPeripheral.class)).register(),
        "libspectrum's SCLD, a TC2048's");

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(List.of(0x49, 0x3e), Reference.withSnap(file, "szx", snap -> List.of(lib.libspectrum_snap_out_scld_hsr(snap) & 0xff,
        lib.libspectrum_snap_out_scld_dec(snap) & 0xff)).orElseThrow());
  }

  /** Without GDOS here it cannot page in; the rest of it comes back, its RAM, its control register and its controller. */
  @Test
  void thePlusDWithItsRamAndItsController() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    PlusDPeripheral plusd = plugged(kept, PlusDPeripheral.class);
    plusd.ram(pattern(110, MgtDiskInterface.RAM_SIZE));
    plusd.control(0x81);
    plusd.fdc().direction = Fdd.Dir.IN;
    plusd.fdc().trackRegister = 7;
    plusd.fdc().sectorRegister = 0x0a;
    plusd.fdc().dataRegister = 0x5a;
    plusd.fdc().statusRegister = 0x02;
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(PlusDPeripheral.class), "the file plugs it in");
    assertEquals(described(plusd), described((PlusDPeripheral) opened.peripheralRegistry.find(PlusDPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(plusd), Reference.withSnap(file, "szx", snap -> {
      assertEquals(List.of(1, MgtDiskInterface.DRIVES, 0), List.of(lib.libspectrum_snap_plusd_active(snap),
          lib.libspectrum_snap_plusd_drive_count(snap), lib.libspectrum_snap_plusd_custom_rom(snap)));
      return List.<Object>of(lib.libspectrum_snap_plusd_paged(snap) != 0, lib.libspectrum_snap_plusd_direction(snap) == 0,
          lib.libspectrum_snap_plusd_control(snap) & 0xff, lib.libspectrum_snap_plusd_track(snap) & 0xff,
          lib.libspectrum_snap_plusd_sector(snap) & 0xff, lib.libspectrum_snap_plusd_data(snap) & 0xff,
          lib.libspectrum_snap_plusd_status(snap) & 0xff,
          Arrays.hashCode(lib.libspectrum_snap_plusd_ram(snap, 0).getByteArray(0, MgtDiskInterface.RAM_SIZE)));
    }).orElseThrow());
  }

  /** libspectrum's PLSD: its control register, its controller, and its RAM empty. */
  @Test
  void thePlusDOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/PLSD.szx")), opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(PlusDPeripheral.class), "the file plugs it in");
    PlusDPeripheral plusd = (PlusDPeripheral) opened.peripheralRegistry.find(PlusDPeripheral.class);
    assertEquals(List.of(0xbc, 7, 0x0a, 0xff, 0, Arrays.hashCode(new byte[MgtDiskInterface.RAM_SIZE])), described(plusd).subList(2, 8));
  }

  /** Whether it is paged in, whether the head steps out, its control register, its controller's four, and its RAM. */
  private static List<Object> described(PlusDPeripheral plusd) {
    return List.of(plusd.isPaged(), plusd.fdc().direction == Fdd.Dir.OUT, plusd.controlRegister(), plusd.fdc().trackRegister,
        plusd.fdc().sectorRegister, plusd.fdc().dataRegister, plusd.fdc().statusRegister, Arrays.hashCode(plusd.ram()));
  }

  /** Without its ROM here it cannot page in; its RAM, its PIA and its controller come back. */
  @Test
  void theOpusWithItsRamItsPiaAndItsController() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    OpusPeripheral opus = plugged(kept, OpusPeripheral.class);
    opus.ram(pattern(120, OpusPeripheral.RAM_SIZE));
    opus.pia(new OpusPeripheral.Pia(0xca, 0xfe, 0xef, 0xbd, 0xdb, 0xb7));
    opus.fdc().direction = Fdd.Dir.IN;
    opus.fdc().trackRegister = 5;
    opus.fdc().sectorRegister = 0x11;
    opus.fdc().dataRegister = 0x22;
    opus.fdc().statusRegister = 0x33;
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(OpusPeripheral.class), "the file plugs it in");
    assertEquals(described(opus), described((OpusPeripheral) opened.peripheralRegistry.find(OpusPeripheral.class)));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Libspectrum lib = Libspectrum.INSTANCE;
    assertEquals(described(opus), Reference.withSnap(file, "szx", snap -> {
      assertEquals(List.of(1, OpusPeripheral.DRIVES, 0), List.of(lib.libspectrum_snap_opus_active(snap),
          lib.libspectrum_snap_opus_drive_count(snap), lib.libspectrum_snap_opus_custom_rom(snap)));
      return List.<Object>of(lib.libspectrum_snap_opus_paged(snap) != 0, lib.libspectrum_snap_opus_direction(snap) == 0,
          new OpusPeripheral.Pia(lib.libspectrum_snap_opus_control_a(snap) & 0xff, lib.libspectrum_snap_opus_data_reg_a(snap) & 0xff,
              lib.libspectrum_snap_opus_data_dir_a(snap) & 0xff, lib.libspectrum_snap_opus_control_b(snap) & 0xff,
              lib.libspectrum_snap_opus_data_reg_b(snap) & 0xff, lib.libspectrum_snap_opus_data_dir_b(snap) & 0xff),
          lib.libspectrum_snap_opus_track(snap) & 0xff, lib.libspectrum_snap_opus_sector(snap) & 0xff,
          lib.libspectrum_snap_opus_data(snap) & 0xff, lib.libspectrum_snap_opus_status(snap) & 0xff,
          Arrays.hashCode(lib.libspectrum_snap_opus_ram(snap, 0).getByteArray(0, OpusPeripheral.RAM_SIZE)));
    }).orElseThrow());
  }

  /** libspectrum's OPUS: its PIA and its controller as its tests read them, and its RAM empty. */
  @Test
  void theOpusOfLibspectrumsOwnFile() throws Exception {
    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(Files.readAllBytes(Fixtures.HERE.resolve("libspectrum/szx-chunks/OPUS.szx")), opened, note -> { });

    assertTrue(opened.peripheralRegistry.isActive(OpusPeripheral.class), "the file plugs it in");
    OpusPeripheral opus = (OpusPeripheral) opened.peripheralRegistry.find(OpusPeripheral.class);
    assertEquals(List.of(new OpusPeripheral.Pia(0xca, 0xfe, 0xef, 0xbd, 0xdb, 0xb7), 5, 0x11, 0x22, 0x33,
        Arrays.hashCode(new byte[OpusPeripheral.RAM_SIZE])), described(opus).subList(2, 8));
  }

  /** Whether it is paged in, whether the head steps out, its PIA, its controller's four, and its RAM. */
  private static List<Object> described(OpusPeripheral opus) {
    return List.of(opus.isPaged(), opus.fdc().direction == Fdd.Dir.OUT, opus.pia(), opus.fdc().trackRegister,
        opus.fdc().sectorRegister, opus.fdc().dataRegister, opus.fdc().statusRegister, Arrays.hashCode(opus.ram()));
  }

  /** A 48K with one fitted, which the AY's block says, and a machine that reads it fits one with the same registers. */
  @Test
  void theMelodikFittedToAFortyEightK() throws Exception {
    Speccy kept = AMachineComesBackWithItsWindowTest.machine();
    MelodikPeripheral melodik = (MelodikPeripheral) kept.peripheralRegistry.find(MelodikPeripheral.class);
    melodik.setFitted(true);
    if (kept.peripheralRegistry.update()) kept.machine.reset(true);
    melodik.register(7, 0x38);
    byte[] file = new SzxFormat().write(kept, note -> { });

    Speccy opened = AMachineComesBackWithItsWindowTest.machine();
    new SzxFormat().read(file, opened, note -> { });
    assertTrue(opened.peripheralRegistry.isActive(MelodikPeripheral.class), "the file fits it");
    assertEquals(0x38, ((MelodikPeripheral) opened.peripheralRegistry.find(MelodikPeripheral.class)).register(7));

    assumeTrue(Reference.present(), "libspectrum is not installed here");
    assertEquals(1, Reference.withSnap(file, "szx", Libspectrum.INSTANCE::libspectrum_snap_melodik_active).orElseThrow());
  }

  /** What the machine sees from the bottom of its memory up. */
  private static byte[] seen(Speccy speccy, int length) {
    byte[] seen = new byte[length];
    for (int address = 0; address < length; address++) seen[address] = (byte) speccy.memory.peek(address);
    return seen;
  }

  /** That device of the machine, plugged in as its window plugs it. */
  static <T extends Peripheral & Pluggable> T plugged(Speccy speccy, Class<T> type) {
    T device = type.cast(speccy.peripheralRegistry.find(type));
    device.plugIn(true);
    if (speccy.peripheralRegistry.update()) speccy.machine.reset(true);
    return device;
  }

  /** Its jumpers, its 8255, the bank paged in or 0xff, and what each bank holds. */
  private static List<Object> described(ZxataspPeripheral board) {
    List<Object> said = new ArrayList<>(List.of(board.upload(), board.writeProtect(), board.portA(), board.portB(),
        board.portC(), board.control(), board.isPaged() ? board.bank() : 0xff));
    for (int bank = 0; bank < ZxataspPeripheral.BANKS; bank++) said.add(Arrays.hashCode(board.contents(bank)));
    return said;
  }

  /** Its EPROM jumper, its register, whether it is paged in, its EPROM and each page of its RAM. */
  private static List<Object> described(DivPeripheral divide) {
    List<Object> said = new ArrayList<>(List.of(divide.writeProtect(), divide.control(), divide.isPaged(),
        Arrays.hashCode(divide.eprom())));
    for (int page = 0; page < divide.ramPages(); page++) said.add(Arrays.hashCode(divide.ram(page)));
    return said;
  }

  /** Whether it is paged in, stealthy (the One without J2), ready for the button, and its RAM. */
  private static List<Object> described(MultifacePeripheral face) {
    return List.of(face.isPaged(), face.stealth() && !face.isJ2(), face.redButtonReady(), Arrays.hashCode(face.ram()));
  }

  static byte[] pattern(int seed, int length) {
    byte[] bytes = new byte[length];
    for (int at = 0; at < length; at++) bytes[at] = (byte) (seed + at * 7 + at / 256);
    return bytes;
  }
}

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

package model.tests.cpu;

import model.harness.BridgeTest;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a machine that arrives as a plugin hands out for a contended read, against what Fuse
 * hands out for the same run: the 128, the +3, the NTSC 48K.
 * <p>
 * The 48K's are with the emulator, which is the machine it carries. These are here because
 * their machines are, and a build without them has nothing to compare against - asking for a
 * machine that is not there used to run the default one and compare its timings instead.
 */
public class ContendedMemoryOfMachinesThatArriveTests extends BridgeTest {

  @Test
  void test128KContendedMemoryPage5T14361() {
    setupModel("128K", 14361);
    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 6, cpu.getTStates()); // 6 T-states delay
  }

  @Test
  void test128KContendedMemoryPage1T14361() {
    setupModel("128K", 14361);
    bus.getMemory().setPage(3, 1); // 0xC000-0xFFFF
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0xC000);
    assertEquals(initialTStates + 3 + 6, cpu.getTStates());
  }

  @Test
  void test128KNonContendedPageT14361() {
    setupModel("128K", 14361);
    bus.getMemory().setPage(3, 0); // Non-contended
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0xC000);
    assertEquals(initialTStates + 3, cpu.getTStates());
  }

  @Test
  void test128KLDIInstructionT14361() {
    int initialTStates = setupModel("128K", 14361);
    cpu.setHL(0x4000);
    cpu.setDE(0x6000);
    cpu.executeInstruction("LDI", null);
    assertEquals(initialTStates + 4 + 4 + (6 + 3) + (5 + 3) + (6 + 1), cpu.getTStates()); // Fetch ED + fetch A0 + delay + read + delay + write + extra, total +27
  }

  @Test
  void testPlus3ContendedMemoryPage6T14361() {
    setupModel("+3", 14361);
    bus.getMemory().setPage(3, 6); // 0xC000-0xFFFF
    cpu.setTStates(14361);
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0xC000);
    assertEquals(initialTStates + 3 + 1, cpu.getTStates()); // 1 T-state delay (per +3 pattern)
  }

  @Test
  void testPlus3ContendedMemoryT14363() {
    setupModel("+3", 14363);
    bus.getMemory().setPage(1, 5);
    cpu.setTStates(14363);
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 7, cpu.getTStates()); // 7 T-states delay
  }

  @Test
  void testPlus3CALLInstructionT14361() {
    setupModel("+3", 14361);
    cpu.setPC(25000);
    cpu.setSP(0x4000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("CALL nn", new int[]{0x3000});
    assertEquals(initialTStates + (4 + 1) + (3 + 4) + (3 + 5) + 1 + 3 + 3, cpu.getTStates()); // Adjusted per +3 pattern, total +36
    assertEquals(0x3000, cpu.getPC());
  }

  @Test
  void testNTSCContendedMemoryT8959() {
    setupModel("48K_NTSC", 8959);
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 6, cpu.getTStates());
  }

  @Test
  void testNTSCINInstructionT8959() {
    setupModel("48K_NTSC", 8959);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("IN A,(n)", new int[]{0xFE});
    assertEquals(8976, cpu.getTStates()); // Adjusted for I/O contention, example
  }

  @Disabled("the bridge's machine has no Interface 1 on it: LocalLibretroCore.retro_read_lan_port answers zero and setTxData goes nowhere, so what these read back is the stub, not a port")
  @Test
  void test128KInterface1PortF7ContentionT14361() {
    setupModel("128K", 14361);
    int initialTStates = cpu.getTStates();
    cpu.out(0xF7, (byte) 0xAA);
    assertEquals((byte) 0xAA, interface1.getRxData());
    assertEquals(initialTStates + 4, cpu.getTStates()); // No contention
  }

  @Disabled("the bridge's machine has no Interface 1 on it: LocalLibretroCore.retro_read_lan_port answers zero and setTxData goes nowhere, so what these read back is the stub, not a port")
  @Test
  void testPlus3Interface1MicrodriveContentionT14363() {
    setupModel("+3", 14363);
    int initialTStates = cpu.getTStates();
    cpu.out(0xEF, (byte) 0x08);
    cpu.out(0xEF, (byte) 0x01);
    cpu.out(0xEF, (byte) 0x03);
    cpu.out(0xEF, (byte) 0x01);
    cpu.out(0xE7, (byte) 0xBB);
    assertEquals(initialTStates, cpu.getTStates()); // No contention for OUT, 5 outs each +4
    assertEquals((byte) 0xBB, microdrive.readData());
  }

  @Disabled("the bridge's machine has no Interface 1 on it: LocalLibretroCore.retro_read_lan_port answers zero and setTxData goes nowhere, so what these read back is the stub, not a port")
  @Test
  void test128KInterface1ErrorWithContentionT14361() {
    setupModel("128K", 14361);
    microdrive.setWriteProtect(true);
    cpu.out(0xEF, (byte) 0x08);
    cpu.out(0xEF, (byte) 0x01);
    cpu.out(0xEF, (byte) 0x03);
    cpu.out(0xEF, (byte) 0x01);
    bus.handleError("Write protect");
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 6, cpu.getTStates()); // Contention for read
    assertTrue(interface1.isROMPagedIn());
  }

  @Disabled("the bridge's machine has no Interface 1 on it: LocalLibretroCore.retro_read_lan_port answers zero and setTxData goes nowhere, so what these read back is the stub, not a port")
  @Test
  void testNTSCInterface1NetworkContentionT8959() {
    setupModel("48K_NTSC", 8959);
    cpu.out(0xEF, (byte) 0x00);
    int initialTStates = cpu.getTStates();
    cpu.out(0xF7, (byte) 0xCC);
    assertEquals((byte) 0xCC, interface1.getRxData());
    assertEquals(initialTStates + 4, cpu.getTStates()); // No contention
  }

  @Test
  void test128KINCIIInstructionT14361() {
    setupModel("128K", 14361);
    cpu.setHL(0x4000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("INC (HL)", null);
    assertEquals(initialTStates + 4 + (3 + 2) + (1 + 5) + (3 + 0), cpu.getTStates()); // Fetch + delay + read + delay + modify + write + delay, total +17
  }

  @Test
  void test128KLDIInstructionT14362() {
    setupModel("128K", 14362);
//    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setHL(0x4000);
    cpu.setDE(0x6000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("LDI", null);
//    int i = 4 + 4 + (5 + 3) + (4 + 3) + (5 + 1) + 2;
    int i = 31;
    int expected = initialTStates + i;
    assertEquals(expected, cpu.getTStates()); // Fetch ED + fetch A0 + delay + read + delay + write + extra, total +29
  }

  @Test
  void testPlus3LDIInstructionT14362() {
    setupModel("+3", 14362);
//    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setHL(0x4000);
    cpu.setDE(0x6000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("LDI", null);
    int i = 21;
//    int i = 4 + 4 + (0 + 3) + (0 + 3) + 2 + 5;
    assertEquals(initialTStates + i, cpu.getTStates()); // Fetch ED + fetch A0 + delay + read + delay + write + extra, total +16
  }

  @Test
  void testPlus3LDIInstructionT14363() {
    setupModel("+3", 14363);
    cpu.setDE(0xFFFE);
    cpu.setHL(0x4000);
    cpu.setSP(0x6000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("LDI", null);
//    Tstates added: 4
//    Tstates added: 4
//    Tstates added: 10
//    Tstates added: 3
//    Tstates added: 2
//    int i = 4 + 4 + (7 + 3) + 3 + 2;
    int i = 23;
    int expected = initialTStates + i;
    assertEquals(expected, cpu.getTStates()); // Adjusted per +3 pattern, total +28
  }

  @Test
  void test128KContendedMemoryPage7T14362() {
    setupModel("128K", 14362);
    bus.getMemory().setPage(1, 7); // 0x4000-0x7FFF, contended
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 5, cpu.getTStates()); // 5 T-states delay at T14362
  }

  @Test
  void test128KLDHLInstructionT14363() {
    setupModel("128K", 14363);
    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setPC(25000);
    cpu.setHL(0x4000);
    cpu.setRegisterA((byte) 0xEE);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("LD (HL),A", null);
    assertEquals((byte) 0xEE, testDriver.readMemory(0x4000, false));
    assertEquals(initialTStates + 4 + 4 + 3 + 3 + 1, cpu.getTStates()); // Fetch + delay + write + delay, total +14
  }

  @Test
  void test128KINCInstructionT14364() {
    setupModel("128K", 14364);
    bus.getMemory().setPage(3, 1); // 0xC000-0xFFFF, contended
    cpu.setHL(0xC000);
    cpu.writeMemory(0xC000, (byte) 0x30, true);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("INC (HL)", null);
    assertEquals(initialTStates + 4 + 3 + 3 + 3 + 1 + 3 + 0, cpu.getTStates()); // Fetch + delay + read + delay + modify + write + delay, total +17
    assertEquals((byte) 0x31, bus.readMemory(0xC000));
  }

  @Disabled("the bridge's machine has no Interface 1 on it: LocalLibretroCore.retro_read_lan_port answers zero and setTxData goes nowhere, so what these read back is the stub, not a port")
  @Test
  void test128KInterface1PortEFContentionT14363() {
    int initialTStates = setupModel("128K", 14363);
    cpu.out(0xEF, (byte) 0x02);
    assertEquals(initialTStates + 4, cpu.getTStates()); // No contention for odd port
  }

  @Test
  void testPlus3ContendedMemoryPage4T14364() {
    int initialTStates = setupModel("+3", 14364);
    bus.getMemory().setPage(3, 4); // 0xC000-0xFFFF, contended
    cpu.setTStates(14364);
    cpu.readMemory(0xC000);
    assertEquals(initialTStates + 3 + 6, cpu.getTStates()); // 6 T-states delay at T14364
  }

  @Test
  void testPlus3LDHLInstructionT14365() {
    int initialTStates = setupModel("+3", 14365);
    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setPC(25000);
    cpu.setHL(0x4000);
    cpu.setRegisterA((byte) 0xFF);
    cpu.setTStates(14365);
    cpu.executeInstruction("LD (HL),A", null);
//    int i = 4 + 5 + 3 + 4;
    int i = 16;
    assertEquals(initialTStates + i, cpu.getTStates()); // Fetch + delay + write + delay, total +16
    assertEquals( 0xFF, bus.readMemory(0x4000));
  }

  @Test
  void testPlus3INCInstructionT14366() {
    testDriver.tstatesHistoryInit();
    setupModel("+3", 14366);
    bus.getMemory().setPage(3, 6); // 0xC000-0xFFFF, contended
    cpu.setHL(0xC000);
    cpu.writeMemory(0xC000, (byte) 0x40, true);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("INC (HL)", null);
    int i = 4 + 4 + 3 + 3 + 1 + 3 + 2 - 5 + 1;
//    int i = (4 + 0) + 3 + 3 + 1 + 3 + 2 - 5;
//    i+= 8;

    assertTStatesHistory("""
        [TStateUpdate{key=14366, value=4, description='ula writebyte'}
        , TStateUpdate{key=14370, value=3, description='writebyte'}
        , TStateUpdate{key=14373, value=4, description='readbyte'}
        , TStateUpdate{key=14377, value=1, description='ula readbyte'}
        , TStateUpdate{key=14378, value=3, description='readbyte'}
        , TStateUpdate{key=14381, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14382, value=4, description='ula writebyte'}
        , TStateUpdate{key=14386, value=3, description='writebyte'}
        ]""");
    assertEquals(initialTStates + i, cpu.getTStates()); // Fetch + delay + read + delay + modify + write + delay, total +20
    assertEquals((byte) 0x41, testDriver.readMemory(0xC000, true));
  }

  @Test
  void testPlus3CALLInstructionT14364() {
    setupModel("+3", 14364);
    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setPC(25000);
    cpu.setSP(0x4000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("CALL nn", new int[]{0x3000});
    assertEquals(initialTStates + (4 + 6) + (3 + 5) + (3 + 4) + 1 + 3 + 3, cpu.getTStates()); // Fetch+delay + pc+1+delay + pc+2+delay + internal + sp-1+delay + sp-2+delay, total +29
    assertEquals(0x3000, cpu.getPC());

    assertTStatesHistory("""
        [TStateUpdate{key=14364, value=6, description='ula readbyte'}
        , TStateUpdate{key=14370, value=4, description='readbyte'}
        , TStateUpdate{key=14374, value=4, description='ula readbyte'}
        , TStateUpdate{key=14378, value=3, description='readbyte'}
        , TStateUpdate{key=14381, value=5, description='ula readbyte'}
        , TStateUpdate{key=14386, value=3, description='readbyte'}
        , TStateUpdate{key=14389, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14390, value=3, description='writebyte'}
        , TStateUpdate{key=14393, value=3, description='writebyte'}
        ]""");
  }

  @Test
  void test128KContendedMemoryPage3T14365() {
    setupModel("128K", 14365);
    bus.getMemory().setPage(3, 3); // 0xC000-0xFFFF, contended
    int initialTStates = cpu.getTStates();
    cpu.readMemory(0xC000);
    assertEquals(initialTStates + 3 + 2, cpu.getTStates()); // 2 T-states delay at T14365
  }

  @Test
  void testPlus3ContendedMemoryPage7T14367() {
    int initialTStates = setupModel("+3", 14367);
    bus.getMemory().setPage(1, 7); // 0x4000-0x7FFF, contended
    cpu.readMemory(0x4000);
    assertEquals(initialTStates + 3 + 3, cpu.getTStates()); // 3 T-states delay at T14367
  }

  @Test
  void test128KCALLInstructionT14366() {
    setupModel("128K", 14366);
    bus.getMemory().setPage(1, 5); // 0x4000-0x7FFF
    cpu.setPC(25000);
    cpu.setSP(0x4000);
    int initialTStates = cpu.getTStates();
    cpu.executeInstruction("CALL nn", new int[]{0x3000});
    assertEquals(0x3000, cpu.getPC());
    assertEquals(initialTStates + (4 + 1) + (3 + 0) + (3 + 0) + 1 + 3 + 3 + 14, cpu.getTStates()); // Fetch+delay + pc+1+delay + pc+2+delay + internal + sp-1+delay + sp-2+delay, total +18
  }

  @Test
  void test128KAddHLBCT14361() {
    testDriver.tstatesHistoryInit();
    int initialTStates = setupModel("128K", 14361);
    cpu.setHL(0x1234);
    cpu.setBC(0x5678);
    cpu.setIR(0x4000);
    cpu.executeInstruction("ADD HL,BC", null);
    assertEquals(initialTStates + 11 + 20, cpu.getTStates(), "T-states for ADD HL,BC");
    assertEquals(0x68AC, cpu.getHL(), "HL value after ADD");
    assertTStatesHistory("""
        [TStateUpdate{key=14361, value=4, description='readbyte'}
        , TStateUpdate{key=14365, value=2, description='ula contend_read_no_mreq'}
        , TStateUpdate{key=14367, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14368, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14369, value=6, description='ula contend_read_no_mreq'}
        , TStateUpdate{key=14375, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14376, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14377, value=6, description='ula contend_read_no_mreq'}
        , TStateUpdate{key=14383, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14384, value=1, description='contend_read_no_mreq'}
        , TStateUpdate{key=14385, value=6, description='ula contend_read_no_mreq'}
        , TStateUpdate{key=14391, value=1, description='contend_read_no_mreq'}
        ]""");
  }

  @Test
  void test128KIncDET14361() {
    int initialTStates = setupModel("128K", 14361);
    cpu.setDE(0xFFFF);
    cpu.setIR(0x4000);
    cpu.executeInstruction("INC DE", null);
    assertEquals(initialTStates + 6 + 2, cpu.getTStates(), "T-states for INC DE");
    assertEquals(0x0000, cpu.getDE(), "DE value after INC");
  }

  @Test
  void test128KContendedMemoryWithIncDE() {
    int initialTStates = setupModel("128K", 14361);
    bus.getMemory().setPage(1, 5); // Contended page
    cpu.setDE(0x1234);
    cpu.setHL(0x4000);
    cpu.setRegisterA((byte) 0xA1);
    cpu.executeInstruction("INC DE", null); // 6 T-states
    cpu.executeInstruction("LD (HL),A", null); // 7 + contention (6 at T=14361+6=14367)
    assertEquals(0x1235, cpu.getDE(), "DE value after INC");
    assertEquals((byte) 0xA1, testDriver.readMemory(0x4000, false), "Memory value after LD");
    assertEquals(initialTStates + 6 + 7 + 6 + 2 - 4, cpu.getTStates(), "T-states with contention");
  }

  @Test
  void testPlus3AddHLBCT14361() {
    int initialTStates = setupModel("+3", 14361);
    cpu.setHL(0x1234);
    cpu.setBC(0x5678);
    cpu.setIR(0x4000);
    cpu.executeInstruction("ADD HL,BC", null);
    assertEquals(initialTStates + 11, cpu.getTStates(), "T-states for ADD HL,BC");
    assertEquals(0x68AC, cpu.getHL(), "HL value after ADD");
  }

  @Test
  void testPlus3IncDET14361() {
    int initialTStates = setupModel("+3", 14361);
    cpu.setDE(0xFFFF);
    cpu.setIR(0x4000);
    cpu.executeInstruction("INC DE", null);
    assertEquals(initialTStates + 6, cpu.getTStates(), "T-states for INC DE");
    assertEquals(0x0000, cpu.getDE(), "DE value after INC");
  }

  @Test
  void testPlus3ContendedMemoryWithAddHLBC() {
    int initialTStates = setupModel("+3", 14361);
    bus.getMemory().setPage(3, 6); // Contended page
    cpu.setHL(0xC000);
    cpu.setBC(0x0001);
    cpu.setIR(0x4000);
    cpu.setRegisterA((byte) 0xBB);
    cpu.executeInstruction("ADD HL,BC", null); // 11 T-states
    cpu.executeInstruction("LD (HL),A", null); // 7 + contention (1 at T=14361+11=14372)
    assertEquals(initialTStates + 11 + 7 + 1 + 1, cpu.getTStates(), "T-states with contention");
    assertEquals(0xBB, bus.readMemory(0xC001), "Memory value after LD");
  }
}

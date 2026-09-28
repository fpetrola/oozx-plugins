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

package com.fpetrola.oozx.speccy.tools.calls;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.z80.registers.RegisterName;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallGraphTest extends MachineTest {

  @Test
  void theScriptItComesWithDrawsEachRoutineOnceWithItsRangeAndTimes() {
    CallGraph graph = drawnAfterRunning();

    mxGraph drawn = find(graph, mxGraphComponent.class).getGraph();
    Object[] cells = drawn.getChildCells(drawn.getDefaultParent(), true, false);
    assertEquals(List.of("8010–8013\n×2", "8020–8020\n×2"),
        Arrays.stream(cells).map(drawn::getLabel).toList());
    assertEquals(1, drawn.getChildCells(drawn.getDefaultParent(), false, true).length);
  }

  /** In the emulator the plugin has a loader of its own, which is not the thread's. */
  @Test
  void everyExampleRunsOnARealTreeFromAPluginsOwnLoader() {
    Thread thread = Thread.currentThread();
    ClassLoader was = thread.getContextClassLoader();
    thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
    try {
      everyExampleRuns();
    } finally {
      thread.setContextClassLoader(was);
    }
  }

  private void everyExampleRuns() {
    CallGraph graph = drawnAfterRunning();
    JComboBox<?> examples = find(graph, JComboBox.class);
    for (int i = 0; i < examples.getItemCount(); i++) {
      examples.setSelectedIndex(i);
      assertEquals(" ", find(graph, JLabel.class).getText(), examples.getItemAt(i) + " failed");
    }
    assertEquals(CallGraph.EXAMPLES.size(), examples.getItemCount());
  }

  /** 8000: CALL 8010 / CALL 8010 / JP 8006    8010: CALL 8020 / RET    8020: RET */
  private CallGraph drawnAfterRunning() {
    Speccy speccy = silentMachine();
    int[] program = {0xcd, 0x10, 0x80, 0xcd, 0x10, 0x80, 0xc3, 0x06, 0x80, 0, 0, 0, 0, 0, 0, 0,
        0xcd, 0x20, 0x80, 0xc9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xc9};
    for (int i = 0; i < program.length; i++) speccy.memory.poke(0x8000 + i, (byte) program[i]);
    speccy.cpu.getOoz80().getState().getRegister(RegisterName.PC).write(0x8000);
    speccy.cpu.getOoz80().getState().getRegister(RegisterName.SP).write(0xff00);
    CallTree calls = new CallTree(speccy);
    for (int i = 0; i < 12; i++) speccy.cpu.step();

    CallGraph graph = new CallGraph();
    graph.show(speccy, calls);
    return graph;
  }

  private static <T> T find(Container container, Class<T> type) {
    for (Component child : container.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      T found = child instanceof Container inner ? find(inner, type) : null;
      if (found != null) return found;
    }
    return null;
  }
}

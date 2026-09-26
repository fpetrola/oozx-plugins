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

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxPoint;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que llamo a que, como grafo: un nodo por rutina, con cuantas veces se la llamo, y una flecha
 * a cada una que llama, puestas por niveles. Se arrastra para moverse y la rueda acerca o aleja.
 */
final class CallGraph extends JPanel {

  private final mxGraph graph = new mxGraph();
  private final mxGraphComponent component = new mxGraphComponent(graph);
  private final JCheckBox decimal = new JCheckBox("Decimal");
  private List<CallTree.Call> shown = List.of();

  CallGraph() {
    super(new BorderLayout());
    graph.setCellsEditable(false);
    graph.setCellsDisconnectable(false);
    Map<String, Object> edges = graph.getStylesheet().getDefaultEdgeStyle();
    edges.put(mxConstants.STYLE_ROUNDED, true);
    edges.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ELBOW);
    component.setConnectable(false);
    draggingMovesTheView();
    wheelZooms();
    add(component, BorderLayout.CENTER);
    decimal.addActionListener(e -> show(shown));
    add(decimal, BorderLayout.NORTH);
  }

  /**
   * Dibujado de nuevo entero: cada rutina una vez aunque la llamen de varios lados, con lo que
   * ocupa de punta a punta y cuantas veces se la llamo.
   */
  void show(List<CallTree.Call> program) {
    shown = program;
    Map<Integer, int[]> routines = new LinkedHashMap<>();
    Set<List<Integer>> calls = new LinkedHashSet<>();
    program.forEach(call -> walk(call, routines, calls));
    graph.getModel().beginUpdate();
    try {
      graph.removeCells(graph.getChildCells(graph.getDefaultParent()));
      Map<Integer, Object> nodes = new LinkedHashMap<>();
      routines.forEach((address, seen) -> nodes.put(address, graph.insertVertex(graph.getDefaultParent(), null,
          said(seen[1]) + "–" + said(seen[2]) + "\n×" + seen[0], 0, 0, 100, 36)));
      calls.forEach(call -> graph.insertEdge(graph.getDefaultParent(), null, "",
          nodes.get(call.get(0)), nodes.get(call.get(1))));
      mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
      layout.setIntraCellSpacing(40.0);
      layout.setInterRankCellSpacing(60.0);
      layout.execute(graph.getDefaultParent());
    } finally {
      graph.getModel().endUpdate();
    }
  }

  private String said(int address) {
    return (decimal.isSelected() ? "%d" : "%04X").formatted(address);
  }

  /** Veces, desde y hasta de cada rutina, juntando todos los lugares desde donde la llamaron. */
  private static void walk(CallTree.Call call, Map<Integer, int[]> routines, Set<List<Integer>> calls) {
    routines.merge(call.address(), new int[]{call.times(), call.from(), call.to()}, (was, more) ->
        new int[]{was[0] + more[0], Math.min(was[1], more[1]), Math.max(was[2], more[2])});
    for (CallTree.Call made : call.made()) {
      calls.add(List.of(call.address(), made.address()));
      walk(made, routines, calls);
    }
  }

  private void draggingMovesTheView() {
    MouseAdapter dragging = new MouseAdapter() {
      private mxPoint start;

      public void mousePressed(MouseEvent e) {
        mxPoint translate = graph.getView().getTranslate();
        double scale = graph.getView().getScale();
        start = new mxPoint(e.getX() / scale - translate.getX(), e.getY() / scale - translate.getY());
      }

      public void mouseDragged(MouseEvent e) {
        if (start == null) return;
        double scale = graph.getView().getScale();
        graph.getView().setTranslate(new mxPoint(e.getX() / scale - start.getX(), e.getY() / scale - start.getY()));
        e.consume();
      }

      public void mouseReleased(MouseEvent e) {
        start = null;
      }
    };
    component.getGraphControl().addMouseListener(dragging);
    component.getGraphControl().addMouseMotionListener(dragging);
  }

  /** Acerca o aleja dejando quieto lo que esta bajo el puntero. */
  private void wheelZooms() {
    component.getGraphControl().addMouseWheelListener((MouseWheelEvent e) -> {
      mxGraphView view = graph.getView();
      double scale = Math.round(view.getScale() * (e.getWheelRotation() < 0 ? 1.2 : 1 / 1.2) * 100) / 100.0;
      if (scale <= 0.04 || scale == view.getScale()) return;
      mxPoint before = component.getPointForEvent(e, false);
      view.setScale(scale);
      mxPoint after = component.getPointForEvent(e, false);
      mxPoint translate = view.getTranslate();
      view.setTranslate(new mxPoint(translate.getX() + after.getX() - before.getX(),
          translate.getY() + after.getY() - before.getY()));
      e.consume();
    });
  }
}

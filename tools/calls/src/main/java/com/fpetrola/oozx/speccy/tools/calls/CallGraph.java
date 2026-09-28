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
import com.fpetrola.oozx.speccy.devices.InstructionListing;
import com.fpetrola.oozx.speccy.modules.z80.Disassembly;
import com.fpetrola.z80.registers.RegisterName;
import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.canvas.mxICanvas;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxGraphModel;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxPoint;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.AbstractAction;
import javax.swing.CellRendererPane;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Lo que llamo a que, como grafo: lo arma un script de JavaScript que se edita a la izquierda, con
 * la maquina y el arbol de llamadas a mano, y que dice que cajas y flechas hay; aca se ponen por
 * niveles. Las cajas se eligen, se mueven y se agrandan; arrastrar el fondo mueve la vista y la
 * rueda acerca o aleja.
 */
final class CallGraph extends JPanel {

  private static final List<String> KEYWORDS = List.of("var", "function", "return", "if", "else", "for",
      "while", "in", "new", "true", "false", "null", "Math", "String", "Java.type", "print");

  /** Lo alto de la franja con el rango, arriba de la tabla de cada caja. */
  private static final int HEADER = 18;
  /** Cuanto tarda, mas o menos, la vista en llegar a lo que se esta ejecutando cuando lo sigue. */
  private static final double SMOOTH_SECONDS = 0.35;

  private final InstructionListing listing = new InstructionListing();
  private final CellRendererPane stamp = new CellRendererPane();
  private final mxGraph graph = new mxGraph() {
    public void drawState(mxICanvas canvas, mxCellState state, boolean drawLabel) {
      super.drawState(canvas, state, drawLabel);
      if (((mxCell) state.getCell()).getValue() instanceof Code code && canvas instanceof mxGraphics2DCanvas drawing) {
        stamp(drawing, state, code);
      }
    }

    public boolean isCellSelectable(Object cell) {
      return getModel().isVertex(cell);
    }
  };
  private final mxGraphComponent component = new mxGraphComponent(graph);
  private final JCheckBox decimal = new JCheckBox("Decimal");
  private final JCheckBox opcodes = new JCheckBox("Opcodes");
  private final JCheckBox follow = new JCheckBox("Follow");
  private final Timer following = new Timer(15, e -> followStep());
  /** Las cajas que se movieron o agrandaron a mano: el layout no las vuelve a poner. */
  private final Set<String> placed = new HashSet<>();
  private final double[] velocity = new double[2];
  private long lastStep;

  /** Una caja que muestra instrucciones en vez de un texto: la tabla del debugger, estampada. */
  private record Code(String label, List<Disassembly.Line> lines) {
    public String toString() {
      return label;
    }

    int row(int address) {
      for (int row = 0; row < lines.size(); row++) {
        if (lines.get(row).address() == address) return row;
      }
      return -1;
    }
  }

  /** Lo que se puede elegir arriba del editor, y el archivo que trae cada uno. */
  static final Map<String, String> EXAMPLES = new LinkedHashMap<>();

  static {
    EXAMPLES.put("Who calls whom", "call-graph.js");
    EXAMPLES.put("Most called", "most-called.js");
    EXAMPLES.put("Every path", "every-path.js");
    EXAMPLES.put("Where it is now", "stack.js");
    EXAMPLES.put("By memory block", "memory-blocks.js");
    EXAMPLES.put("Where they return", "returns.js");
    EXAMPLES.put("With its code", "with-its-code.js");
  }

  private static final String PRELUDE = script("prelude.js");

  private final JComboBox<String> examples = new JComboBox<>(EXAMPLES.keySet().toArray(String[]::new));
  private final RSyntaxTextArea script = new RSyntaxTextArea(script("call-graph.js"), 12, 60);
  private final JLabel problem = new JLabel(" ");
  private final ScriptEngine engine = new NashornScriptEngineFactory()
      .getScriptEngine(new String[]{"--language=es6"}, CallGraph.class.getClassLoader());
  private Speccy machine;
  private CallTree calls;

  CallGraph() {
    super(new BorderLayout());
    graph.setCellsEditable(false);
    graph.setCellsDisconnectable(false);
    graph.setDropEnabled(false);
    graph.addListener(mxEvent.CELLS_MOVED, this::placedByHand);
    graph.addListener(mxEvent.CELLS_RESIZED, this::placedByHand);
    Map<String, Object> edges = graph.getStylesheet().getDefaultEdgeStyle();
    edges.put(mxConstants.STYLE_ROUNDED, true);
    edges.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ELBOW);
    component.setConnectable(false);
    component.getGraphControl().add(stamp);
    draggingMovesTheView();
    wheelZooms();
    script.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
    script.setCodeFoldingEnabled(true);
    script.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "run");
    script.getActionMap().put("run", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        show(machine, calls);
      }
    });
    AutoCompletion completion = new AutoCompletion(completions());
    completion.setAutoActivationEnabled(true);
    completion.install(script);
    examples.addActionListener(e -> {
      script.setText(script(EXAMPLES.get((String) examples.getSelectedItem())));
      script.setCaretPosition(0);
      show(machine, calls);
    });
    decimal.addActionListener(e -> show(machine, calls));
    opcodes.addActionListener(e -> {
      listing.showBytes(opcodes.isSelected());
      show(machine, calls);
    });
    listing.showBytes(false);
    follow.addActionListener(e -> {
      if (follow.isSelected()) following.start();
    });
    JPanel editor = new JPanel(new BorderLayout());
    editor.add(examples, BorderLayout.NORTH);
    editor.add(new RTextScrollPane(script), BorderLayout.CENTER);
    editor.add(problem, BorderLayout.SOUTH);
    JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
    options.add(decimal);
    options.add(opcodes);
    options.add(follow);
    JPanel drawn = new JPanel(new BorderLayout());
    drawn.add(options, BorderLayout.NORTH);
    drawn.add(component, BorderLayout.CENTER);
    JSplitPane both = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, drawn);
    both.setResizeWeight(0.3);
    add(both, BorderLayout.CENTER);
  }

  /**
   * Lo que dice el script, puesto sobre lo que ya estaba: cada caja y cada flecha tiene el id que
   * le dio el script, asi que se agrega lo nuevo, se saca lo que ya no esta y lo demas se queda
   * donde estaba y seleccionado. Solo se vuelve a ordenar por niveles si aparecio o se fue algo,
   * y lo que se puso a mano queda donde se lo puso.
   */
  void show(Speccy machine, CallTree calls) {
    this.machine = machine;
    this.calls = calls;
    Map<String, Object> nodes = new LinkedHashMap<>();
    Map<String, List<String>> arrows = new LinkedHashMap<>();
    if (calls != null && !ran((id, label) -> nodes.put("v" + id(id), shown(label)),
        (from, to) -> arrows.put("e" + id(from) + ">" + id(to), List.of("v" + id(from), "v" + id(to))))) return;
    mxGraphModel model = (mxGraphModel) graph.getModel();
    Object parent = graph.getDefaultParent();
    model.beginUpdate();
    try {
      List<Object> gone = Stream.of(graph.getChildCells(parent))
          .filter(cell -> !nodes.containsKey(((mxCell) cell).getId()) && !arrows.containsKey(((mxCell) cell).getId())).toList();
      graph.removeCells(gone.toArray());
      boolean moved = !gone.isEmpty();
      for (Map.Entry<String, Object> node : nodes.entrySet()) {
        mxCell cell = (mxCell) model.getCell(node.getKey());
        mxRectangle size = size(node.getValue());
        if (cell == null) {
          graph.insertVertex(parent, node.getKey(), node.getValue(), 0, 0, size.getWidth(), size.getHeight(),
              style(node.getValue()));
          moved = true;
        } else {
          if (!node.getValue().equals(cell.getValue())) {
            model.setValue(cell, node.getValue());
            model.setStyle(cell, style(node.getValue()));
          }
          mxGeometry geometry = cell.getGeometry();
          if (!placed.contains(node.getKey())
              && (geometry.getWidth() != size.getWidth() || geometry.getHeight() != size.getHeight())) {
            geometry = (mxGeometry) geometry.clone();
            geometry.setWidth(size.getWidth());
            geometry.setHeight(size.getHeight());
            model.setGeometry(cell, geometry);
            moved = true;
          }
        }
      }
      for (Map.Entry<String, List<String>> arrow : arrows.entrySet()) {
        Object from = model.getCell(arrow.getValue().get(0));
        Object to = model.getCell(arrow.getValue().get(1));
        if (model.getCell(arrow.getKey()) == null && from != null && to != null) {
          graph.insertEdge(parent, arrow.getKey(), "", from, to);
          moved = true;
        }
      }
      if (moved) laidOut(model);
    } finally {
      model.endUpdate();
    }
  }

  private void laidOut(mxGraphModel model) {
    Map<String, mxGeometry> byHand = new HashMap<>();
    placed.forEach(id -> {
      if (model.getCell(id) instanceof mxCell cell) byHand.put(id, cell.getGeometry());
    });
    mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
    layout.setIntraCellSpacing(40.0);
    layout.setInterRankCellSpacing(60.0);
    layout.execute(graph.getDefaultParent());
    byHand.forEach((id, geometry) -> model.setGeometry(model.getCell(id), geometry));
  }

  private void placedByHand(Object sender, mxEventObject event) {
    for (Object cell : (Object[]) event.getProperty("cells")) {
      if (graph.getModel().isVertex(cell)) placed.add(((mxCell) cell).getId());
    }
  }

  private static String style(Object value) {
    return value instanceof Code ? "verticalAlign=top;align=left;spacingLeft=6;fillColor=#E8EEF7;fontColor=#1A1A1A" : null;
  }

  private mxRectangle size(Object value) {
    return value instanceof Code code
        ? new mxRectangle(0, 0, opcodes.isSelected() ? 300 : 210, HEADER + code.lines().size() * listing.getRowHeight())
        : new mxRectangle(0, 0, 100, 36);
  }

  /** Corre el script; si falla, lo dice abajo del editor y el grafo queda como estaba. */
  private boolean ran(BiConsumer<Object, Object> node, BiConsumer<Object, Object> edge) {
    Bindings bindings = engine.createBindings();
    bindings.put("machine", machine);
    bindings.put("calls", calls);
    bindings.put("listing", new Disassembly(machine));
    bindings.put("decimal", decimal.isSelected());
    bindings.put("node", node);
    bindings.put("edge", edge);
    try {
      engine.eval(PRELUDE, bindings);
      engine.eval(script.getText(), bindings);
      problem.setText(" ");
      return true;
    } catch (ScriptException | RuntimeException e) {
      problem.setText(e.getMessage());
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private Object shown(Object label) {
    if (!(label instanceof List<?> shown) || shown.isEmpty()) return String.valueOf(label);
    List<Disassembly.Line> lines = (List<Disassembly.Line>) shown;
    return new Code(said(lines.get(0).address()) + "–" + said(lines.get(lines.size() - 1).address()), lines);
  }

  private String said(int address) {
    return decimal.isSelected() ? String.valueOf(address) : "%04X".formatted(address);
  }

  private int pc() {
    return machine == null ? -1 : machine.cpu.getOoz80().getState().getRegister(RegisterName.PC).read();
  }

  /**
   * La tabla dibujada en el lugar de la caja, a su tamaño sin zoom y escalada despues, con la
   * instruccion que la maquina va a ejecutar elegida; un sello, como las celdas de una JTable.
   */
  private void stamp(mxGraphics2DCanvas drawing, mxCellState state, Code code) {
    double scale = graph.getView().getScale();
    Graphics2D g = (Graphics2D) drawing.getGraphics().create();
    g.translate(state.getX() + drawing.getTranslate().getX(), state.getY() + drawing.getTranslate().getY());
    g.scale(scale, scale);
    listing.decimal(decimal.isSelected());
    listing.show(code.lines());
    listing.select(pc());
    stamp.paintComponent(g, listing, component.getGraphControl(), 1, HEADER,
        (int) (state.getWidth() / scale) - 2, (int) (state.getHeight() / scale) - HEADER - 1, true);
    g.dispose();
  }

  /**
   * Un paso de la vista hacia la instruccion que se va a ejecutar, sin tocar el zoom: la mueve
   * como un resorte con amortiguacion critica, que arranca y frena suave y no se pasa de largo.
   */
  private void followStep() {
    if (!follow.isSelected() || !isShowing()) {
      following.stop();
      lastStep = 0;
      return;
    }
    long now = System.nanoTime();
    double seconds = lastStep == 0 ? 0.015 : Math.min(0.1, (now - lastStep) / 1e9);
    lastStep = now;
    int pc = pc();
    for (Object cell : graph.getChildVertices(graph.getDefaultParent())) {
      if (((mxCell) cell).getValue() instanceof Code code && code.row(pc) >= 0) {
        mxGeometry box = ((mxCell) cell).getGeometry();
        mxGraphView view = graph.getView();
        Rectangle visible = component.getViewport().getViewRect();
        double y = box.getY() + HEADER + (code.row(pc) + 0.5) * listing.getRowHeight();
        mxPoint translate = view.getTranslate();
        double x = towards(translate.getX(), visible.getCenterX() / view.getScale() - box.getCenterX(), 0, seconds);
        double nowY = towards(translate.getY(), visible.getCenterY() / view.getScale() - y, 1, seconds);
        if (Math.abs(x - translate.getX()) > 0.01 || Math.abs(nowY - translate.getY()) > 0.01) {
          view.setTranslate(new mxPoint(x, nowY));
        }
        return;
      }
    }
  }

  /** El SmoothDamp de siempre: la aproximacion de un resorte criticamente amortiguado. */
  private double towards(double current, double target, int axis, double seconds) {
    double omega = 2 / SMOOTH_SECONDS;
    double x = omega * seconds;
    double decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
    double change = current - target;
    double temp = (velocity[axis] + omega * change) * seconds;
    velocity[axis] = (velocity[axis] - omega * temp) * decay;
    return target + (change + temp) * decay;
  }

  /** Un id como lo escribiria JavaScript: 4096 y 4096.0 y "4096" son el mismo nodo. */
  private static String id(Object said) {
    return said instanceof Number number && number.doubleValue() == number.longValue()
        ? String.valueOf(number.longValue()) : String.valueOf(said);
  }

  private static String script(String file) {
    try (InputStream in = CallGraph.class.getResourceAsStream(file)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Lo que el script tiene a mano: las palabras de JavaScript y lo publico de lo que recibe. */
  private static DefaultCompletionProvider completions() {
    DefaultCompletionProvider provider = new DefaultCompletionProvider();
    KEYWORDS.forEach(word -> provider.addCompletion(new BasicCompletion(provider, word)));
    Map.of("machine", "the Speccy running", "calls", "the CallTree watching it",
            "node", "node(id, label) draws a box", "edge", "edge(from, to) draws an arrow",
            "said", "said(address) in hex or decimal, as the view is", "visit", "visit(fn(call, caller, path)) over the whole tree",
            "routines", "routines() each once: times, from, to, calls",
            "listing", "the Disassembly: between(first, last), from(address, lines)",
            "code", "code(id, from, to, most) draws a box with those instructions",
            "decimal", "whether the view says numbers in decimal")
        .forEach((name, said) -> provider.addCompletion(new BasicCompletion(provider, name, said)));
    Stream.of(Speccy.class, CallTree.class, CallTree.Call.class, Disassembly.class)
        .flatMap(type -> Stream.<Member>concat(Stream.of(type.getMethods()), Stream.of(type.getFields())))
        .filter(member -> member.getDeclaringClass() != Object.class && !Modifier.isStatic(member.getModifiers()))
        .map(member -> member instanceof Method ? member.getName() + "()" : member.getName())
        .distinct()
        .forEach(name -> provider.addCompletion(new BasicCompletion(provider, name)));
    return provider;
  }

  private void draggingMovesTheView() {
    MouseAdapter dragging = new MouseAdapter() {
      private mxPoint start;

      public void mousePressed(MouseEvent e) {
        if (component.getCellAt(e.getX(), e.getY()) != null) return;
        follow.setSelected(false);
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

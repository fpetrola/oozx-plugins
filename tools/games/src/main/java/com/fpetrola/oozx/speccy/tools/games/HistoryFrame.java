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

package com.fpetrola.oozx.speccy.tools.games;

import com.fpetrola.oozx.speccy.config.OOZxConfiguration;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.KeepsItsPlace;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryFrame extends JInternalFrame implements KeepsItsPlace {
  private final OOZxConfiguration config = Desk.theOne().configuration();
  private JTable historyTable;
  private SnapshotHistoryTableModel tableModel;

  public HistoryFrame() {
    super("Snapshot History", true, true, true, true);
    setSize(700, 400);
    setLocation(100, 100);

    // Panel principal
    JPanel mainPanel = new JPanel(new BorderLayout());

    tableModel = new SnapshotHistoryTableModel();
    historyTable = new JTable(tableModel);
    historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    historyTable.setRowHeight(25);
    
    // Configurar anchos de columnas
    historyTable.getColumnModel().getColumn(0).setPreferredWidth(250);
    historyTable.getColumnModel().getColumn(1).setPreferredWidth(300);
    historyTable.getColumnModel().getColumn(2).setPreferredWidth(120);
    
    for (OOZxConfiguration.SnapshotHistoryEntry entry : config.getSnapshotHistory().values()) {
      tableModel.addEntry(entry);
    }
    
    historyTable.addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int row = historyTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
          historyTable.setRowSelectionInterval(row, row);
          if (e.getClickCount() == 2) {
            OOZxConfiguration.SnapshotHistoryEntry selected = getSelectedEntry();
            if (selected != null) load(selected);
          }
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        int row = historyTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
          historyTable.setRowSelectionInterval(row, row);
        }
        if (e.isPopupTrigger()) {
          showContextMenu(e);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          showContextMenu(e);
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {}

      @Override
      public void mouseExited(MouseEvent e) {}
    });

    JScrollPane scrollPane = new JScrollPane(historyTable);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    // Panel de botones
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    
    JButton loadButton = new JButton("Load");
    loadButton.addActionListener(e -> {
      OOZxConfiguration.SnapshotHistoryEntry selected = getSelectedEntry();
      if (selected != null) load(selected);
    });
    buttonPanel.add(loadButton);

    JButton removeButton = new JButton("Remove from History");
    removeButton.addActionListener(e -> {
      int row = historyTable.getSelectedRow();
      if (row >= 0) {
        OOZxConfiguration.SnapshotHistoryEntry selected = tableModel.getEntryAt(row);
        if (selected != null) {
          tableModel.removeEntryAt(row);
          forget(selected);
        }
      }
    });
    buttonPanel.add(removeButton);

    JButton clearButton = new JButton("Clear All");
    clearButton.addActionListener(e -> {
      int confirm = JOptionPane.showConfirmDialog(
          this,
          "Clear all snapshot history?",
          "Confirm",
          JOptionPane.YES_NO_OPTION);
      if (confirm == JOptionPane.YES_OPTION) {
        tableModel.clear();
        forget(null);
      }
    });
    buttonPanel.add(clearButton);

    mainPanel.add(buttonPanel, BorderLayout.SOUTH);

    add(mainPanel);
    
    getRootPane().registerKeyboardAction(
        e -> dispose(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
    );
    
    addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
      @Override
      public void internalFrameClosing(javax.swing.event.InternalFrameEvent e) {
        config.save();
      }
    });
  }

  private void showContextMenu(MouseEvent e) {
    int row = historyTable.getSelectedRow();
    if (row >= 0) {
      JPopupMenu menu = new JPopupMenu();
      
      JMenuItem viewDetailsItem = new JMenuItem("View Details");
      viewDetailsItem.addActionListener(ev -> {
        OOZxConfiguration.SnapshotHistoryEntry selected = tableModel.getEntryAt(row);
        if (selected != null) Desk.theOne().showDetails(new Desk.Game(null, null, null,
            selected.getGameName().replaceAll("\\.(z80|sna|tap|tzx|szx)$", "").trim()));
      });
      menu.add(viewDetailsItem);
      
      menu.addSeparator();
      
      JMenuItem loadItem = new JMenuItem("Load");
      loadItem.addActionListener(ev -> {
        OOZxConfiguration.SnapshotHistoryEntry selected = tableModel.getEntryAt(row);
        if (selected != null) load(selected);
      });
      menu.add(loadItem);
      
      JMenuItem removeItem = new JMenuItem("Remove from History");
      removeItem.addActionListener(ev -> {
        OOZxConfiguration.SnapshotHistoryEntry selected = tableModel.getEntryAt(row);
        if (selected != null) {
          tableModel.removeEntryAt(row);
          forget(selected);
        }
      });
      menu.add(removeItem);
      
      menu.show(historyTable, e.getX(), e.getY());
    }
  }

  public OOZxConfiguration.SnapshotHistoryEntry getSelectedEntry() {
    int row = historyTable.getSelectedRow();
    if (row >= 0) {
      return tableModel.getEntryAt(row);
    }
    return null;
  }

  private void load(OOZxConfiguration.SnapshotHistoryEntry entry) {
    Desk.theOne().openSaved(entry.getInitialStateId(), entry.getFilePath());
  }

  private void forget(OOZxConfiguration.SnapshotHistoryEntry entry) {
    if (entry == null) {
      config.getSnapshotHistory().clear();
    } else {
      config.getSnapshotHistory().remove(new java.io.File(entry.getFilePath()).getAbsolutePath());
    }
    config.save();
  }

  private void refreshHistory() {
    tableModel.clear();
    for (OOZxConfiguration.SnapshotHistoryEntry entry : config.getSnapshotHistory().values()) {
      tableModel.addEntry(entry);
    }
  }

  public OOZxConfiguration.WindowState saveWindowState() {
    return new OOZxConfiguration.WindowState(
        "SNAPSHOT_HISTORY", getX(), getY(), getWidth(), getHeight());
  }

  public void restoreWindowState(OOZxConfiguration.WindowState state) {
    if (state != null) {
      if (state.getWidth() > 0 && state.getHeight() > 0) {
        setSize(state.getWidth(), state.getHeight());
      }
      if (state.getX() >= 0 && state.getY() >= 0) {
        setLocation(state.getX(), state.getY());
      }
    }
  }

  /**
   * Rows are snapshots and the newest is on top.
   */
  private static class SnapshotHistoryTableModel extends AbstractTableModel {
    private List<OOZxConfiguration.SnapshotHistoryEntry> entries = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    
    private static final String[] COLUMN_NAMES = {"Game Name", "File Path", "Loaded Time"};
    private static final int[] COLUMN_WIDTHS = {250, 300, 120};

    @Override
    public int getRowCount() {
      return entries.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      OOZxConfiguration.SnapshotHistoryEntry entry = entries.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return entry.getGameName();
        case 1:
          return entry.getFilePath();
        case 2:
          return dateFormat.format(new Date(entry.getLoadedTime()));
        default:
          return "";
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    public void addEntry(OOZxConfiguration.SnapshotHistoryEntry entry) {
      entries.add(entry);
      fireTableRowsInserted(entries.size() - 1, entries.size() - 1);
    }

    public void removeEntryAt(int row) {
      if (row >= 0 && row < entries.size()) {
        entries.remove(row);
        fireTableRowsDeleted(row, row);
      }
    }

    public OOZxConfiguration.SnapshotHistoryEntry getEntryAt(int row) {
      if (row >= 0 && row < entries.size()) {
        return entries.get(row);
      }
      return null;
    }

    public void clear() {
      int size = entries.size();
      entries.clear();
      if (size > 0) {
        fireTableRowsDeleted(0, size - 1);
      }
    }
  }
}

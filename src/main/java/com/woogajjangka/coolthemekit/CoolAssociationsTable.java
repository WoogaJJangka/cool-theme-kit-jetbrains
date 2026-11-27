package com.woogajjangka.coolthemekit;

import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedHashMap;
import java.util.Map;

/** A table of names and the icons the user gives them, with add and remove buttons (one VS Code associations setting). */
final class CoolAssociationsTable {
    private final DefaultTableModel model;
    private final JBTable table;
    private final JComponent component;

    CoolAssociationsTable(@NotNull String nameColumn, @NotNull String iconColumn) {
        model = new DefaultTableModel(new Object[]{nameColumn, iconColumn}, 0);
        table = new JBTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setShowGrid(false);
        table.getEmptyText().setText(CoolThemeKitBundle.message("settings.associations.empty"));
        component = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> {
                    stopEditing();
                    model.addRow(new Object[]{"", ""});
                    int row = model.getRowCount() - 1;
                    table.getSelectionModel().setSelectionInterval(row, row);
                    table.editCellAt(row, 0);
                })
                .setRemoveAction(button -> {
                    stopEditing();
                    int[] rows = table.getSelectedRows();
                    for (int i = rows.length - 1; i >= 0; i--) model.removeRow(rows[i]);
                })
                .disableUpDownActions()
                .setPreferredSize(new java.awt.Dimension(-1, 120))
                .createPanel();
    }

    @NotNull JComponent getComponent() {
        return component;
    }

    /** The rows with a name and an icon, in their order; a name given twice keeps its last icon. */
    @NotNull Map<String, String> getValue() {
        stopEditing();
        Map<String, String> value = new LinkedHashMap<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String name = text(model.getValueAt(row, 0));
            String icon = text(model.getValueAt(row, 1));
            if (!name.isEmpty() && !icon.isEmpty()) value.put(name, icon);
        }
        return value;
    }

    void setValue(@NotNull Map<String, String> value) {
        stopEditing();
        model.setRowCount(0);
        value.forEach((name, icon) -> model.addRow(new Object[]{name, icon}));
    }

    private void stopEditing() {
        if (table.isEditing() && table.getCellEditor() != null) table.getCellEditor().stopCellEditing();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}

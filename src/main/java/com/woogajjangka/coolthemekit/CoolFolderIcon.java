package com.woogajjangka.coolthemekit;

import com.intellij.ui.icons.IconReplacer;
import com.intellij.ui.icons.ReplaceableIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.CellRendererPane;
import javax.swing.Icon;
import javax.swing.JTree;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.Objects;

/**
 * A folder icon that shows the open folder while its row is expanded, as VS Code does in its explorer.
 *
 * <p>The tree renderers of the IDE ask a node for one icon and never say whether the node is expanded, so the icon finds
 * out when it is painted: a tree paints its rows with a {@link CellRendererPane} that it owns, placing the renderer at the
 * bounds of the row, so the row is the one at the renderer's position. Painted anywhere else (an editor tab, a popup) it
 * is the closed folder. Expanding or collapsing a row repaints it, so nothing has to be updated.
 */
final class CoolFolderIcon implements Icon, ReplaceableIcon {
    private final Icon closed;
    private final Icon open;

    CoolFolderIcon(@NotNull Icon closed, @NotNull Icon open) {
        this.closed = closed;
        this.open = open;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        (isExpandedRow(c) ? open : closed).paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return closed.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return closed.getIconHeight();
    }

    /** Keeps both icons through the replacements of the IDE (the dark icon of a selected row, a disabled icon). */
    @Override
    public @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
        return new CoolFolderIcon(replacer.replaceIcon(closed), replacer.replaceIcon(open));
    }

    /** Whether {@code c} is being painted as a row of a tree that is expanded. */
    static boolean isExpandedRow(Component c) {
        Component row = c;
        while (row != null && !(row.getParent() instanceof CellRendererPane)) {
            row = row.getParent();
        }
        if (row == null) return false;
        Container pane = row.getParent();
        if (!(pane.getParent() instanceof JTree tree)) return false;
        // The pane belongs to the tree and is not moved, so the bounds of the renderer are those of the row in the tree.
        int middle = row.getY() + row.getHeight() / 2;
        int index = tree.getClosestRowForLocation(row.getX(), middle);
        if (index < 0) return false;
        Rectangle bounds = tree.getRowBounds(index);
        return bounds != null && middle >= bounds.y && middle < bounds.y + bounds.height && tree.isExpanded(index);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CoolFolderIcon other && closed.equals(other.closed) && open.equals(other.open);
    }

    @Override
    public int hashCode() {
        return Objects.hash(closed, open);
    }

    @Override
    public String toString() {
        return "CoolFolderIcon(" + closed + ", " + open + ")";
    }
}

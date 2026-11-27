package com.woogajjangka.coolthemekit;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

/** Settings | Appearance &amp; Behavior | Cool Theme Kit. */
public final class CoolIconsConfigurable implements Configurable {
    private JBCheckBox roleMarks;

    @Override
    public @Nls String getDisplayName() {
        return CoolThemeKitBundle.message("settings.title");
    }

    @Override
    public @Nullable JComponent createComponent() {
        roleMarks = new JBCheckBox(CoolThemeKitBundle.message("settings.roleMarks"));
        JBLabel roleMarksHint = new JBLabel(CoolThemeKitBundle.message("settings.roleMarks.hint"));
        roleMarksHint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        roleMarksHint.setFontColor(UIUtil.FontColor.BRIGHTER);
        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(roleMarks)
                .addComponentToRightColumn(roleMarksHint, 0)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        return roleMarks.isSelected() != CoolIconsSettings.getInstance().getState().roleMarks;
    }

    @Override
    public void apply() {
        CoolIconsSettings.getInstance().setRoleMarks(roleMarks.isSelected());
    }

    @Override
    public void reset() {
        roleMarks.setSelected(CoolIconsSettings.getInstance().getState().roleMarks);
    }

    @Override
    public void disposeUIResources() {
        roleMarks = null;
    }
}

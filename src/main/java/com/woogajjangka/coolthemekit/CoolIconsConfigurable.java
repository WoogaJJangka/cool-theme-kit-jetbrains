package com.woogajjangka.coolthemekit;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Settings | Appearance &amp; Behavior | Cool Theme Kit. */
public final class CoolIconsConfigurable implements Configurable {
    /** The entry of the accent list for a color that is not a preset */
    private static final CoolAccent.Preset CUSTOM = new CoolAccent.Preset("custom", "", null);

    private JBCheckBox roleMarks;
    private ComboBox<String> iconStyle;
    private CoolAssociationsTable filesAssociations;
    private CoolAssociationsTable foldersAssociations;
    private CoolAssociationsTable languagesAssociations;
    private ComboBox<CoolAccent.Preset> accentPreset;
    private ColorPanel accentColor;
    private final List<JBCheckBox> targets = new ArrayList<>();
    private boolean updating;

    @Override
    public @Nls String getDisplayName() {
        return CoolThemeKitBundle.message("settings.title");
    }

    private static JBLabel hint(String text) {
        JBLabel hint = new JBLabel(text);
        hint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        hint.setFontColor(UIUtil.FontColor.BRIGHTER);
        hint.setAllowAutoWrapping(true);
        return hint;
    }

    @Override
    public @Nullable JComponent createComponent() {
        roleMarks = new JBCheckBox(CoolThemeKitBundle.message("settings.roleMarks"));
        iconStyle = new ComboBox<>(new String[]{CoolIconsSettings.GLASS, CoolIconsSettings.FLAT});
        iconStyle.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList<? extends String> list, String style, int index, boolean selected, boolean focused) {
                if (style != null) setText(CoolThemeKitBundle.message("settings.iconStyle." + style));
            }
        });
        filesAssociations = new CoolAssociationsTable(CoolThemeKitBundle.message("settings.associations.files.name"),
                CoolThemeKitBundle.message("settings.associations.icon"));
        foldersAssociations = new CoolAssociationsTable(CoolThemeKitBundle.message("settings.associations.folders.name"),
                CoolThemeKitBundle.message("settings.associations.icon"));
        languagesAssociations = new CoolAssociationsTable(CoolThemeKitBundle.message("settings.associations.languages.name"),
                CoolThemeKitBundle.message("settings.associations.icon"));

        List<CoolAccent.Preset> presets = new ArrayList<>(CoolAccent.PRESETS);
        presets.add(CUSTOM);
        accentPreset = new ComboBox<>(presets.toArray(CoolAccent.Preset[]::new));
        accentPreset.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList<? extends CoolAccent.Preset> list, CoolAccent.Preset preset, int index,
                                  boolean selected, boolean focused) {
                if (preset == null) return;
                if (preset == CUSTOM) {
                    setText(CoolThemeKitBundle.message("settings.accent.custom"));
                    setIcon(null);
                    return;
                }
                String name = CoolThemeKitBundle.message("settings.accent.preset." + preset.id());
                setText(preset.source() == null ? name : name + "  (" + preset.source() + ")");
                setIcon(JBUIScale.scaleIcon(new ColorIcon(12, CoolAccent.toColor(preset.color()))));
            }
        });
        accentColor = new ColorPanel();
        accentColor.setSupportTransparency(false);
        accentPreset.addActionListener(event -> {
            if (updating) return;
            CoolAccent.Preset preset = (CoolAccent.Preset) accentPreset.getSelectedItem();
            if (preset != null && preset != CUSTOM) setColor(CoolAccent.toColor(preset.color()));
            accentColor.setEnabled(true);
        });
        accentColor.addActionListener(event -> {
            if (updating) return;
            selectPreset(accentColor.getSelectedColor());
        });
        JPanel accentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accentRow.add(accentPreset);
        accentRow.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(8)));
        accentRow.add(accentColor);

        JPanel targetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        targets.clear();
        for (String target : CoolAccent.TARGETS) {
            JBCheckBox box = new JBCheckBox(CoolThemeKitBundle.message("settings.accent.target." + target));
            box.putClientProperty("target", target);
            targets.add(box);
            targetsPanel.add(box);
            targetsPanel.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(16)));
        }

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(CoolThemeKitBundle.message("settings.iconStyle"), iconStyle)
                .addComponentToRightColumn(hint(CoolThemeKitBundle.message("settings.iconStyle.hint")), 0)
                .addComponent(roleMarks)
                .addComponentToRightColumn(hint(CoolThemeKitBundle.message("settings.roleMarks.hint")), 0)
                .addComponent(new TitledSeparator(CoolThemeKitBundle.message("settings.associations.title")), JBUI.scale(12))
                .addComponent(hint(CoolThemeKitBundle.message("settings.associations.hint")))
                .addLabeledComponent(CoolThemeKitBundle.message("settings.associations.files"), filesAssociations.getComponent(), true)
                .addLabeledComponent(CoolThemeKitBundle.message("settings.associations.folders"), foldersAssociations.getComponent(), true)
                .addLabeledComponent(CoolThemeKitBundle.message("settings.associations.languages"), languagesAssociations.getComponent(), true)
                .addComponent(new TitledSeparator(CoolThemeKitBundle.message("settings.accent.title")), JBUI.scale(12))
                .addLabeledComponent(CoolThemeKitBundle.message("settings.accent.color"), accentRow)
                .addLabeledComponent(CoolThemeKitBundle.message("settings.accent.targets"), targetsPanel)
                .addComponentToRightColumn(hint(CoolThemeKitBundle.message("settings.accent.hint")), 0)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    private void setColor(@Nullable Color color) {
        updating = true;
        try {
            accentColor.setSelectedColor(color);
        } finally {
            updating = false;
        }
    }

    /** Selects the preset of the color, or "Custom"; no color is the Islands blue */
    private void selectPreset(@Nullable Color color) {
        String hex = color == null ? CoolAccent.DEFAULT_ACCENT : CoolAccent.toHex(color);
        CoolAccent.Preset found = CoolAccent.PRESETS.stream().filter(p -> p.color().equals(hex)).findFirst().orElse(CUSTOM);
        updating = true;
        try {
            accentPreset.setSelectedItem(found);
        } finally {
            updating = false;
        }
    }

    /** The accent of the form: null for the Islands blue */
    private @Nullable String chosenAccent() {
        Color color = accentColor.getSelectedColor();
        if (color == null) return null;
        String hex = CoolAccent.toHex(color);
        return CoolAccent.isDefault(hex) ? null : hex;
    }

    private List<String> chosenTargets() {
        List<String> chosen = new ArrayList<>();
        for (JBCheckBox box : targets) if (box.isSelected()) chosen.add((String) box.getClientProperty("target"));
        return chosen;
    }

    @Override
    public boolean isModified() {
        CoolIconsSettings.State state = CoolIconsSettings.getInstance().getState();
        return roleMarks.isSelected() != state.roleMarks
                || !Objects.equals(iconStyle.getSelectedItem(), CoolIconsSettings.flatIcons() ? CoolIconsSettings.FLAT : CoolIconsSettings.GLASS)
                || !filesAssociations.getValue().equals(orEmpty(state.filesAssociations))
                || !foldersAssociations.getValue().equals(orEmpty(state.foldersAssociations))
                || !languagesAssociations.getValue().equals(orEmpty(state.languagesAssociations))
                || !Objects.equals(chosenAccent(), CoolIconsSettings.accent())
                || !chosenTargets().equals(CoolIconsSettings.accentTargets());
    }

    @Override
    public void apply() {
        CoolIconsSettings settings = CoolIconsSettings.getInstance();
        settings.setRoleMarks(roleMarks.isSelected());
        settings.setIcons((String) Objects.requireNonNull(iconStyle.getSelectedItem()), filesAssociations.getValue(),
                foldersAssociations.getValue(), languagesAssociations.getValue());
        if (settings.setAccent(chosenAccent(), chosenTargets())) offerRestart();
    }

    /** The colors of the theme are read once: a restart shows them (in Islands Dark and Light) */
    private static void offerRestart() {
        UIThemeLookAndFeelInfo laf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();
        if (laf == null || !CoolThemeCustomizer.THEMES.containsKey(laf.getName())) return;
        Notification notification = new Notification("Cool Theme Kit",
                CoolThemeKitBundle.message("notification.accent.restart.title"),
                CoolThemeKitBundle.message("notification.accent.restart.content"), NotificationType.INFORMATION);
        notification.addAction(NotificationAction.createSimpleExpiring(
                CoolThemeKitBundle.message("notification.accent.restart.action"),
                () -> ApplicationManager.getApplication().invokeLater(() -> ApplicationManagerEx.getApplicationEx().restart(true))));
        Notifications.Bus.notify(notification);
    }

    @Override
    public void reset() {
        CoolIconsSettings.State state = CoolIconsSettings.getInstance().getState();
        roleMarks.setSelected(state.roleMarks);
        iconStyle.setSelectedItem(CoolIconsSettings.flatIcons() ? CoolIconsSettings.FLAT : CoolIconsSettings.GLASS);
        filesAssociations.setValue(orEmpty(state.filesAssociations));
        foldersAssociations.setValue(orEmpty(state.foldersAssociations));
        languagesAssociations.setValue(orEmpty(state.languagesAssociations));
        String accent = CoolIconsSettings.accent();
        Color color = CoolAccent.toColor(accent != null ? accent : CoolAccent.DEFAULT_ACCENT);
        setColor(color);
        selectPreset(color);
        List<String> chosen = CoolIconsSettings.accentTargets();
        for (JBCheckBox box : targets) box.setSelected(chosen.contains((String) box.getClientProperty("target")));
    }

    private static java.util.Map<String, String> orEmpty(@Nullable java.util.Map<String, String> map) {
        return map == null ? java.util.Map.of() : map;
    }

    @Override
    public void disposeUIResources() {
        roleMarks = null;
        iconStyle = null;
        filesAssociations = null;
        foldersAssociations = null;
        languagesAssociations = null;
        accentPreset = null;
        accentColor = null;
        targets.clear();
    }
}

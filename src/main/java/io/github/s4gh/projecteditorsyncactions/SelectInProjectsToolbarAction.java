package io.github.s4gh.projecteditorsyncactions;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;   // ← Presenter lives here

@ActionID(
    category = "Editor",
    id = "io.github.s4gh.navigator.SelectInProjectsToolbarAction"
)
@ActionRegistration(
    displayName = "#CTL_SelectInProjectsToolbarAction",
    lazy = false
)
@ActionReference(path = "Editors/Toolbars/Default", position = 1500)
@Messages("CTL_SelectInProjectsToolbarAction=Select in Projects")
public final class SelectInProjectsToolbarAction extends AbstractAction implements Presenter.Toolbar {

    public SelectInProjectsToolbarAction() {
        // Label for fallback presenter & menus
        putValue(NAME, Bundle.CTL_SelectInProjectsToolbarAction());

        // Reuse tooltip from the built-in "Select in Projects" action (Ctrl+Shift+1)
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInProjects"
        );
        if (builtIn != null) {
            Object tip = builtIn.getValue(Action.SHORT_DESCRIPTION);
            if (tip instanceof String) {
                putValue(SHORT_DESCRIPTION, tip);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInProjects"
        );
        if (builtIn != null && builtIn.isEnabled()) {
            builtIn.actionPerformed(e);
        }
    }

    @Override
    public Component getToolbarPresenter() {
        // Build a toolbar button that *inherits* enablement & tooltip from the built-in action
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInProjects"
        );

        JButton btn = new JButton();
        if (builtIn != null) {
            // Connect: copies Action properties (including SHORT_DESCRIPTION = tooltip)
            Actions.connect(btn, builtIn);  // from org.openide.awt.Actions
        } else {
            // Fallback if built-in action is unavailable
            btn.setText(Bundle.CTL_SelectInProjectsToolbarAction());
            Object tip = getValue(SHORT_DESCRIPTION);
            if (tip instanceof String) {
                btn.setToolTipText((String) tip);
            }
            btn.addActionListener(this);
        }
        return btn;
        }
}

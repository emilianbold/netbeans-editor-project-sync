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
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;

@ActionID(
    category = "Editor",
    id = "io.github.s4gh.navigator.SelectInFilesToolbarAction"
)
@ActionRegistration(
    displayName = "#CTL_SelectInFilesToolbarAction",
    lazy = false
)
// Note: place after the Projects button and add a separator after BOTH buttons
@ActionReference(
    path = "Editors/Toolbars/Default",
    position = 1510,
    separatorAfter = 1520   // ← adds a separator after this action
)
@Messages("CTL_SelectInFilesToolbarAction=Select in Files")
public final class SelectInFilesToolbarAction extends AbstractAction implements Presenter.Toolbar {

    public SelectInFilesToolbarAction() {
        putValue(NAME, Bundle.CTL_SelectInFilesToolbarAction());
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInFiles"
        );
        if (builtIn != null) {
            Object tip = builtIn.getValue(Action.SHORT_DESCRIPTION);
            if (tip instanceof String) putValue(SHORT_DESCRIPTION, tip);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInFiles"
        );
        if (builtIn != null && builtIn.isEnabled()) builtIn.actionPerformed(e);
    }

    @Override
    public Component getToolbarPresenter() {
        Action builtIn = Actions.forID(
            "Window/SelectDocumentNode",
            "org.netbeans.modules.project.ui.SelectInFiles"
        );
        JButton btn = new JButton();
        if (builtIn != null) {
            // Inherit enablement + tooltip from built-in action
            Actions.connect(btn, builtIn);
        } else {
            btn.setText(Bundle.CTL_SelectInFilesToolbarAction());
            Object tip = getValue(SHORT_DESCRIPTION);
            if (tip instanceof String) {
                btn.setToolTipText((String) tip);
            }
            btn.addActionListener(this);
            btn.setIcon(ImageUtilities.loadImageIcon("icons/class.svg", true));
        }
        return btn;
    }
}
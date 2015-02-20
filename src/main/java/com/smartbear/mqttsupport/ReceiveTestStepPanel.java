package com.smartbear.mqttsupport;

import com.eviware.soapui.impl.wsdl.panels.teststeps.AssertionsPanel;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionsListener;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JComponentInspector;
import com.eviware.soapui.support.components.JInspectorPanel;
import com.eviware.soapui.support.components.JInspectorPanelFactory;
import com.eviware.soapui.support.components.SimpleBindingForm;
import com.eviware.soapui.ui.support.ModelItemDesktopPanel;
import com.jgoodies.binding.PresentationModel;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import java.beans.PropertyChangeEvent;

public class ReceiveTestStepPanel extends MqttConnectedTestStepPanel<ReceiveTestStep> implements AssertionsListener {

    private JComponentInspector<JComponent> assertionInspector;
    private JInspectorPanel inspectorPanel;
    private AssertionsPanel assertionsPanel;

    public ReceiveTestStepPanel(ReceiveTestStep modelItem) {
        super(modelItem);
        buildUI();
        modelItem.addAssertionsListener(this);
    }

    private void buildUI() {

        JComponent mainPanel = buildMainPanel();
        inspectorPanel = JInspectorPanelFactory.build(mainPanel);

        assertionsPanel = buildAssertionsPanel();

        assertionInspector = new JComponentInspector<JComponent>(assertionsPanel, "Assertions ("
                + getModelItem().getAssertionCount() + ")", "Assertions for this Message", true);

        inspectorPanel.addInspector(assertionInspector);

        inspectorPanel.setDefaultDividerLocation(0.6F);
        inspectorPanel.setCurrentInspector("Assertions");

        updateStatusIcon();

        add(inspectorPanel.getComponent());

    }


    private JComponent buildMainPanel(){
        PresentationModel<ReceiveTestStep> pm = new PresentationModel<ReceiveTestStep>(getModelItem());
        SimpleBindingForm form = new SimpleBindingForm(pm);
        buildConnectionSection(form, pm);
        form.appendSeparator();
        form.appendHeading("Listening settings");
        form.appendTextArea("listenedTopics", "Listened topics", "The list of topic filters (one filter per line)");
        buildRadioButtonsFromEnum(form, pm, "On unexpected topic", "onUnexpectedTopic", ReceiveTestStep.UnexpectedTopicBehavior.class);
        buildQosRadioButtons(form, pm);
        form.appendComboBox("expectedMessageType", "Expected message type", ReceiveTestStep.MessageType.values(), "Expected type of a received message");
        buildTimeoutSpinEdit(form, pm, "Timeout");
        return new JScrollPane(form.getPanel());
    }
    private AssertionsPanel buildAssertionsPanel(){
        return new AssertionsPanel(getModelItem());
    }

    private void updateStatusIcon() {
        Assertable.AssertionStatus status = getModelItem().getAssertionStatus();
        switch (status) {
            case FAILED: {
                assertionInspector.setIcon(UISupport.createImageIcon("/failed_assertion.png"));
                inspectorPanel.activate(assertionInspector);
                break;
            }
            case UNKNOWN: {
                assertionInspector.setIcon(UISupport.createImageIcon("/unknown_assertion.png"));
                break;
            }
            case VALID: {
                assertionInspector.setIcon(UISupport.createImageIcon("/valid_assertion.png"));
                inspectorPanel.deactivate();
                break;
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        super.propertyChange(event);
        if(event.getPropertyName().equals("assertionStatus")){
            updateStatusIcon();
        }
    }

    @Override
    public boolean onClose(boolean canCancel) {
        if (super.onClose(canCancel)) {
            assertionsPanel.release();
            inspectorPanel.release();
            getModelItem().removeAssertionsListener(this);
            return true;
        }

        return false;
    }
    private void assertionListChanged(){
        assertionInspector.setTitle(String.format("Assertions (%d)", getModelItem().getAssertionCount()));
    }

    @Override
    public void assertionAdded(TestAssertion assertion) {
        assertionListChanged();
    }

    @Override
    public void assertionRemoved(TestAssertion assertion) {
        assertionListChanged();
    }

    @Override
    public void assertionMoved(TestAssertion assertion, int ix, int offset) {
        assertionListChanged();
    }
}

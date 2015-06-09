package com.smartbear.mqttsupport;

import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.components.SimpleBindingForm;
import com.eviware.soapui.ui.support.ModelItemDesktopPanel;
import com.jgoodies.binding.PresentationModel;
import com.jgoodies.binding.adapter.Bindings;
import com.jgoodies.binding.list.SelectionInList;
import com.jgoodies.binding.value.AbstractValueModel;
import com.jgoodies.binding.value.ValueModel;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

public class MqttConnectedTestStepPanel<MqttTestStep extends MqttConnectedTestStep> extends ModelItemDesktopPanel<MqttTestStep>{

    public final static String[] QOS_NAMES = {"At most once (0)", "At least once (1)", "Exactly once (2)"};

    private final static String LEGACY_CONNECTION_NAME = "Individual (legacy) connection";
    private final static String NEW_CONNECTION_ITEM = "<New Connection...>";
    private final static String CONVERT_BUTTON_CAPTION = "Convert Connection";
    private final static String LEGACY_WARNING = "This test step was created by the old version of the plugin. The current version uses another management model for MQTT connections which allows manage them centrally and share between test steps of a project. Click " + CONVERT_BUTTON_CAPTION + " button to create a new style connection and assign it to the test step.";
    private JButton configureConnectionButton;
    private ConnectionsComboBoxModel connectionsModel;

    public interface UIOption{
        String getTitle();
    }

    class ConfigureConnectionsAction extends AbstractAction{
        public ConfigureConnectionsAction() {
            putValue(Action.SHORT_DESCRIPTION, "Configure MQTT Connections of the Project");
            putValue(Action.SMALL_ICON, UISupport.createImageIcon("com/smartbear/mqttsupport/edit_connections.png"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ConfigureProjectConnectionsDialog.showDialog(getModelItem());
        }
    }

    static class ConnectionComboItem{
        private Connection obj;
        public ConnectionComboItem(Connection connection){
            obj = connection;
        }

        @Override
        public String toString(){
            if(obj.isLegacy()) return LEGACY_CONNECTION_NAME; else return obj.getName();
        }

        public Connection getObject(){return obj;}

        @Override
        public boolean equals(Object op) {
            if(op instanceof ConnectionComboItem){
                return ((ConnectionComboItem) op).obj == obj;
            }
            else{
                return false;
            }
        }
    }

    static class NewConnectionComboItem extends ConnectionComboItem{
        private final static NewConnectionComboItem instance = new NewConnectionComboItem();
        public static NewConnectionComboItem getInstance(){return instance;}
        private NewConnectionComboItem(){
            super(null);
        }

        @Override
        public String toString() {
            return NEW_CONNECTION_ITEM;
        }
    }

    class ConnectionsComboBoxModel extends AbstractListModel<ConnectionComboItem> implements ComboBoxModel<ConnectionComboItem>, ConnectionsListener{

        private ArrayList<ConnectionComboItem> items = new ArrayList<>();
        private boolean connectionCreationInProgress = false;
        public ConnectionsComboBoxModel(){
            updateItems();
        }

        private void updateItems(){
            items.clear();
            List<Connection> list = ConnectionsManager.getAvailableConnections(getModelItem());
            if(list != null){
                for(Connection curParams: list){
                    items.add(new ConnectionComboItem(curParams));
                }
            }
            if(getModelItem().getLegacyConnection() != null){
                items.add(new ConnectionComboItem(getModelItem().getLegacyConnection()));
            }
            items.add(NewConnectionComboItem.getInstance());
            fireContentsChanged(this, -1, -1);
        }


        @Override
        public void setSelectedItem(Object anItem) {
            if(anItem == null){
                getModelItem().setConnection(null);
            }
            else {
                if(anItem instanceof NewConnectionComboItem){
                    connectionCreationInProgress = true;
                    UISupport.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            EditConnectionDialog.Result dialogResult = EditConnectionDialog.createConnection(getModelItem());
                            connectionCreationInProgress = false;
                            if(dialogResult == null) {
                                fireContentsChanged(ConnectionsComboBoxModel.this, -1, -1);
                            }
                            else {
                                Connection newConnection = new Connection(dialogResult.connectionName, dialogResult.connectionParams);
                                ConnectionsManager.addConnection(getModelItem(), newConnection);
                                getModelItem().setConnection(newConnection);
                            }

                        }
                    });
                }
                else {
                    Connection newParams = ((ConnectionComboItem) anItem).getObject();
                    getModelItem().setConnection(newParams);
                    fireContentsChanged(this, -1, -1);
                }
            }
        }

        @Override
        public Object getSelectedItem() {
            if(connectionCreationInProgress) return NewConnectionComboItem.getInstance();
            if(getModelItem().getConnection() == null) return null;
            return new ConnectionComboItem(getModelItem().getConnection());
        }

        @Override
        public void connectionListChanged() {
            updateItems();
        }

        @Override
        public void connectionChanged(Connection connection, String propertyName, Object oldPropertyValue, Object newPropertyValue) {
            if(Utils.areStringsEqual(propertyName, "name")){
                for(int i = 0; i <items.size(); ++i){
                    if(items.get(i).getObject() == connection){
                        fireContentsChanged(connection, i, i);
                        break;
                    }
                }
            }
        }

        @Override
        public int getSize() {
            return items.size();
        }

        @Override
        public ConnectionComboItem getElementAt(int index) {
            return items.get(index);
        }

    }

    public MqttConnectedTestStepPanel(MqttTestStep modelItem) {
        super(modelItem);
    }

    protected void buildConnectionSection(SimpleBindingForm form,  PresentationModel<MqttTestStep> pm) {
        connectionsModel = new ConnectionsComboBoxModel();
        ConnectionsManager.addConnectionsListener(getModelItem(), connectionsModel);

        form.appendHeading("Connection to MQTT Server");
        form.appendComboBox("Connection", connectionsModel, "Choose one of pre-configured connections");
        configureConnectionButton = form.appendButtonWithoutLabel("Configure...", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Connection connection = getModelItem().getConnection();
                EditConnectionDialog.Result dialogResult = EditConnectionDialog.editConnection(connection, getModelItem());
                if (dialogResult != null) {
                    if(!connection.isLegacy()) connection.setName(dialogResult.connectionName);
                    connection.setParams(dialogResult.connectionParams);
                }
            }
        });
        configureConnectionButton.setIcon(UISupport.createImageIcon("com/eviware/soapui/resources/images/options.png"));
        configureConnectionButton.setEnabled(getModelItem().getConnection() != null);

        JTextArea legacyInfoLabel = new JTextArea(LEGACY_WARNING, 0, form.getDefaultTextAreaColumns());
        legacyInfoLabel.setEnabled(false);
        legacyInfoLabel.setLineWrap(true);
        legacyInfoLabel.setWrapStyleWord(true);
        form.addLeftComponent(legacyInfoLabel);

        JButton convertConnectionButton = form.appendButtonWithoutLabel(CONVERT_BUTTON_CAPTION + "...", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            EditConnectionDialog.Result dialogResult = EditConnectionDialog.convertLegacyConnection(getModelItem().getConnection().getParams(), getModelItem());
            if(dialogResult != null){
                Connection newConnection = new Connection(dialogResult.connectionName, dialogResult.connectionParams);
                ConnectionsManager.addConnection(getModelItem(), newConnection);
                getModelItem().setConnection(newConnection);
            }

            }
        });


        ReadOnlyValueModel<Connection> legacyModeAdapter = new ReadOnlyValueModel<>(pm.getModel("connection"), new ReadOnlyValueModel.Converter<Connection>() {
            @Override
            public Object convert(Connection srcValue) {
                return srcValue != null && srcValue.isLegacy();
            }
        });
        Bindings.bind(convertConnectionButton, "visible", legacyModeAdapter);
        Bindings.bind(legacyInfoLabel, "visible", legacyModeAdapter);

    }

    protected void buildQosRadioButtons(SimpleBindingForm form,  PresentationModel<MqttTestStep> pm){
        JPanel qosPanel = new JPanel();
        qosPanel.setLayout(new BoxLayout(qosPanel, BoxLayout.X_AXIS));
        for(int i = 0; i < QOS_NAMES.length; ++i) {
            JRadioButton qosRadio = new JRadioButton(QOS_NAMES[i]);
            Bindings.bind(qosRadio, pm.getModel("qos"), i);
            qosPanel.add(qosRadio);
        }
        form.append("Quality of service", qosPanel);

    }


    protected void buildRadioButtonsFromEnum(SimpleBindingForm form, PresentationModel<MqttTestStep> pm, String label, String propertyName, Class propertyType) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        for(Object option : propertyType.getEnumConstants()){
            UIOption uiOption = (UIOption) option;
            JRadioButton radioButton = new JRadioButton(uiOption.getTitle());
            Bindings.bind(radioButton, pm.getModel(propertyName), option);
            panel.add(radioButton);
        }
        form.append(label, panel);
    }

    protected void buildTimeoutSpinEdit(SimpleBindingForm form, PresentationModel<MqttTestStep> pm, String label){
        JPanel timeoutPanel = new JPanel();
        timeoutPanel.setLayout(new BoxLayout(timeoutPanel, BoxLayout.X_AXIS));
        JSpinner spinEdit = Utils.createBoundSpinEdit(pm, "shownTimeout", 0, Integer.MAX_VALUE, 1);
        spinEdit.setPreferredSize(new Dimension(80, spinEdit.getHeight()));
        timeoutPanel.add(spinEdit);
        JComboBox measureCombo = new JComboBox(MqttConnectedTestStep.TimeMeasure.values());
        Bindings.bind(measureCombo, new SelectionInList<Object>(MqttConnectedTestStep.TimeMeasure.values(), pm.getModel("timeoutMeasure")));
        timeoutPanel.add(measureCombo);
        timeoutPanel.add(new JLabel(" (0 - forever)"));
        form.append(label, timeoutPanel);

    }

    protected void addConnectionActionsToToolbar(JXToolBar toolBar){
        Action configureConnectionsAction = new ConfigureConnectionsAction();
        JButton button = UISupport.createActionButton(configureConnectionsAction, configureConnectionsAction.isEnabled());
        toolBar.add(button);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if(Utils.areStringsEqual(evt.getPropertyName(), "connection")){
            if(connectionsModel != null) connectionsModel.setSelectedItem(new ConnectionComboItem((Connection)evt.getNewValue()));
            configureConnectionButton.setEnabled(evt.getNewValue() != null);
        }
    }

    @Override
    protected boolean release() {
        if(connectionsModel != null) ConnectionsManager.removeConnectionsListener(getModelItem(), connectionsModel);
        return super.release();
    }

}

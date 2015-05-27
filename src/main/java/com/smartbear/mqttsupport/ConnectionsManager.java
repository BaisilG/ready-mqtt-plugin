package com.smartbear.mqttsupport;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.support.ModelSupport;
import com.eviware.soapui.model.workspace.Workspace;
import com.eviware.soapui.support.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ConnectionsManager implements PropertyChangeListener {
    private final static String CONNECTIONS_SETTING_NAME = "MQTTConnections";
    private final static String CONNECTION_SECTION_NAME = "Connection";

    private HashMap<Project, ArrayList<Connection>> connections = new HashMap<>();
    private HashMap<Project, ArrayList<ConnectionsListener>> listeners = new HashMap<>();
    private static ConnectionsManager instance = null;

    private ConnectionsManager(){
    }

    private static ConnectionsManager getInstance(){
        if(instance == null) instance = new ConnectionsManager();
        return instance;
    }

    private ArrayList<Connection> getProjectConnectionsList(ModelItem modelItem, boolean ensureListCreated){
        Project project = ModelSupport.getModelItemProject(modelItem);
        if(project == null) throw new IllegalArgumentException();
        ArrayList<Connection> projectConnections = getInstance().connections.get(project);
        if(projectConnections == null){
            if(!connections.containsKey(project)){
                if(project.isOpen()){
                    projectConnections = grabConnections(project);
                    if(projectConnections != null){
                        for(Connection connection: projectConnections){
                            connection.addPropertyChangeListener(this);
                        }
                    }
                    if(projectConnections == null && ensureListCreated) projectConnections = new ArrayList<>();
                    getInstance().connections.put(project, projectConnections);
                }
                else if(ensureListCreated) {
                    throw new IllegalStateException("Attempt to access to MQTT connections of the project which is not open.");
                }
            }
            else if(ensureListCreated) {
                projectConnections = new ArrayList<>();
                connections.put(project, projectConnections);
            }
        }
        return projectConnections;
    }

    public static Connection getConnection(ModelItem modelItem, String connectionName){
        if(StringUtils.isNullOrEmpty(connectionName)) throw new IllegalArgumentException();
        ArrayList<Connection> connections = getInstance().getProjectConnectionsList(modelItem, false);
        if(connections == null) return null;
        for(Connection connection: connections){
            if(Utils.areStringsEqual(connectionName, connection.getName())) return connection;
        }
        return null;
    }

    public static List<Connection> getAvailableConnections(ModelItem modelItem){
        ArrayList<Connection> projectConnections = getInstance().getProjectConnectionsList(modelItem, false);
        if(projectConnections == null) return null;
        return new ArrayList<Connection>(projectConnections);
    }

    public static void addConnection(ModelItem modelItem, Connection connection){
        Project project = ModelSupport.getModelItemProject(modelItem);
        if(project == null) throw new IllegalArgumentException();
        ArrayList<Connection> projectConnections = getInstance().getProjectConnectionsList(project, true);
        boolean alreadyAdded = false;
        for(Connection curConnection: projectConnections){
            if(curConnection == connection) {
                alreadyAdded = true;
                break;
            }
        }
        if(!alreadyAdded){
            projectConnections.add(connection);
            connection.addPropertyChangeListener(getInstance());
            fireConnectionsListChangedEvent(project);
        }
    };

    public static void removeConnection(ModelItem modelItem, Connection connection){
        Project project = ModelSupport.getModelItemProject(modelItem);
        if(project == null) throw new IllegalArgumentException();
        ArrayList<Connection> projectConnections = getInstance().getProjectConnectionsList(project, true);
        boolean removed = false;
        for(int i = 0; i < projectConnections.size(); ++i){
            if(projectConnections.get(i) == connection) {
                projectConnections.remove(i);
                connection.removePropertyChangeListener(getInstance());
                fireConnectionsListChangedEvent(project);
                return;
            }
        }
    };


    public static void addConnectionsListener(ModelItem modelItem, ConnectionsListener listener){
        Project project = ModelSupport.getModelItemProject(modelItem);
        if(project == null) throw new IllegalArgumentException();

        ArrayList<ConnectionsListener> projectListeners = getInstance().listeners.get(project);
        if(projectListeners == null) {
            projectListeners = new ArrayList<ConnectionsListener>();
            getInstance().listeners.put(project, projectListeners);
        }
        projectListeners.add(listener);
    }

    public static void removeConnectionsListener(ModelItem modelItem, ConnectionsListener listener){
        Project project = ModelSupport.getModelItemProject(modelItem);
        if(project == null) throw new IllegalArgumentException();

        ArrayList<ConnectionsListener> projectListeners = getInstance().listeners.get(project);
        if(projectListeners == null) {
            SoapUI.log("MQTT plugin: Internal error: this object is not subscribed to a connection list change.");
            return;
        }
        projectListeners.remove(listener);

    }


    static void onProjectLoaded(Project project){
//        if(instance == null) return;
//        ArrayList<Connection> projectConnections = grabConnections(project);
//        if(projectConnections != null){
//            for(Connection connection: projectConnections){
//                connection.addPropertyChangeListener(getInstance());
//            }
//        }
//        getInstance().connections.put(project, projectConnections);
//        fireConnectionsListChangedEvent(project);
    }

    static void beforeProjectSaved(Project project){
        //if(instance == null) return;
        saveConnections(project, getInstance().connections.get(project));
    }

    static void onProjectClosed(Project project){
        if(instance == null) return;
        ArrayList<Connection> closedConnections = getInstance().connections.remove(project);
        if(closedConnections != null){
            for(Connection connection: closedConnections){
                connection.removePropertyChangeListener(getInstance());
            }
        }
    }

    private static ArrayList<Connection> grabConnections(Project project){
        ArrayList<Connection> result = null;
        String settingValue = project.getSettings().getString(CONNECTIONS_SETTING_NAME, "");
        if(StringUtils.hasContent(settingValue)) {
            XmlObject root = null;
            try {
                root = XmlObject.Factory.parse(settingValue);
            }
            catch (XmlException e) {
                SoapUI.logError(e);
                return result;
            }
            result = new ArrayList<Connection>();
            XmlObject[] connectionSections = root.selectPath("$this/" + CONNECTION_SECTION_NAME);
            for(XmlObject section : connectionSections){
                Connection connection = new Connection();
                connection.load(section);
                result.add(connection);
            }
        }
        return result;
    }

    private static void saveConnections(Project project, List<Connection> connections){
        if(connections == null || connections.size() == 0){
            project.getSettings().clearSetting(CONNECTIONS_SETTING_NAME);
        }
        else {
            XmlObjectBuilder builder = new XmlObjectBuilder();
            for (Connection connection : connections) {
                XmlObject connectionXml = connection.save();
                builder.addSection(CONNECTION_SECTION_NAME, connectionXml);
            }
            project.getSettings().setString(CONNECTIONS_SETTING_NAME, builder.finish().toString());
        }
    }

    private static void fireConnectionsListChangedEvent(Project project){
        ArrayList<ConnectionsListener> listenersForProject = getInstance().listeners.get(project);
        if(listenersForProject != null){
            for(ConnectionsListener listener: listenersForProject){
                try {
                    listener.connectionListChanged();
                }
                catch (Throwable e){
                    SoapUI.logError(e);
                }
            }
        }
    }

    private static void fireConnectionChangedEvent(Project project, Connection connection, String propertyName, Object oldValue, Object newValue){
        ArrayList<ConnectionsListener> listenersForProject = getInstance().listeners.get(project);
        if(listenersForProject != null){
            for(ConnectionsListener listener: listenersForProject){
                try {
                    listener.connectionChanged(connection, propertyName, oldValue, newValue);
                }
                catch (Throwable e){
                    SoapUI.logError(e);
                }
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getSource() instanceof Connection){
            Connection srcConnection = (Connection)evt.getSource();
            Project project = null;
            for(Map.Entry<Project, ArrayList<Connection>> entry: connections.entrySet()){
                for(Connection curConnection: entry.getValue()){
                    if(srcConnection == curConnection){
                        project = entry.getKey();
                    }
                }
            }
            if(project == null){
                assert false;
                return;
            }
            fireConnectionChangedEvent(project, srcConnection, evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    }
}

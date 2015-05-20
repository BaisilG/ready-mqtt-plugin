package com.smartbear.mqttsupport;

import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStepResult;import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.editor.views.xml.outline.support.JsonObjectTree;
import com.eviware.soapui.support.editor.views.xml.outline.support.XmlObjectTree;
import com.jgoodies.binding.PresentationModel;
import com.jgoodies.binding.adapter.SpinnerAdapterFactory;
import com.jgoodies.binding.adapter.SpinnerToValueModelConnector;
import com.jgoodies.binding.value.ValueModel;

import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import java.awt.event.FocusEvent;
import java.net.URI;
import java.net.URISyntaxException;

class Utils {

    public static boolean areStringsEqual(String s1, String s2, boolean caseInsensitive, boolean dontDistinctNullAndEmpty){
        if(dontDistinctNullAndEmpty) {
            if (s1 == null || s1.length() == 0) return s2 == null || s2.length() == 0;
        }
        return areStringsEqual(s1, s2, caseInsensitive);
    }

    public static boolean areStringsEqual(String s1, String s2, boolean caseInsensitive){
        if(s1 == null) return s2 == null;
        if(caseInsensitive) return s1.equalsIgnoreCase(s2); else return s1.equals(s2);
    }
    public static boolean areStringsEqual(String s1, String s2){
        return areStringsEqual(s1, s2, false);
    }

    public static <B> JSpinner createBoundSpinEdit(PresentationModel<B> pm, String propertyName, int minPropValue, int maxPropValue, int step){
        ValueModel valueModel = pm.getModel(propertyName);
        Number defValue = (Number)valueModel.getValue();
        SpinnerModel spinnerModel = new SpinnerNumberModel(defValue, minPropValue, maxPropValue, step);
        SpinnerAdapterFactory.connect(spinnerModel, valueModel, defValue);
        return new JSpinner(spinnerModel);

    }

    public static void showMemo(JTextArea memo, boolean visible){
        memo.setVisible(visible);
        if(memo.getParent() instanceof JScrollPane) {
            memo.getParent().setVisible(visible);
        }
        else if(memo.getParent().getParent() instanceof JScrollPane){
            memo.getParent().getParent().setVisible(visible);
        }

    }

    public static String checkServerUri(String serverUri) {
        if (StringUtils.isNullOrEmpty(serverUri)) {
            return "The Server URI is not specified for the connection.";
        } else {
            URI uri;
            try {
                uri = new URI(serverUri);
                String protocol;
                if(uri.getAuthority() == null){
                    uri = new URI("tcp://" + serverUri);
                    protocol = "tcp";
                }
                else{
                    protocol = uri.getScheme();
                }
                if(protocol != null && !areStringsEqual(protocol, "tcp", false) && !areStringsEqual(protocol, "ssl", false)){
                    return "The Server URI contains unknown protocol. Only \"tcp\" and \"ssl\" are allowed.";
                }
                if(!areStringsEqual(uri.getPath(), "")) return "The Server URI must not contain a path part.";
                if(StringUtils.isNullOrEmpty(uri.getHost())) return "The string specified as Server URI is not a valid URI.";
            } catch (URISyntaxException e) {
                return "The string specified as Server URI is not a valid URI.";
            }
        }
        return null;
    }

    public static String getExceptionMessage(Throwable e){
        String result = StringUtils.hasContent(e.getMessage())? String.format("%s \"%s\"", e.getClass().getName(), e.getMessage()) : e.getClass().getName();
        if(e.getCause() != null){
            result += "; cause: " + getExceptionMessage(e.getCause());
        }
        return result;
    }

    public static String bytesToHexString(byte[] buf){
        final String decimals = "0123456789ABCDEF";
        if(buf == null) return null;
        char[] r = new char[buf.length * 2];
        for(int i = 0; i < buf.length; ++i){
            r[i * 2] = decimals.charAt((buf[i] & 0xf0) >> 4);
            r[i * 2 + 1] = decimals.charAt(buf[i] & 0x0f);
        }
        return new String(r);
    }

    public static byte[] hexStringToBytes(String str){
        if(str == null) return null;
        if(str.length() % 2 != 0) throw new IllegalArgumentException();
        byte[] result = new byte[str.length() / 2];
        try {
            for(int i = 0; i < result.length; ++i){
                result[i] = (byte)Short.parseShort(str.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException();
        }
        return result;
    }



    public static class JsonTreeEditor extends JsonObjectTree {
        private String prevValue = null;
        private boolean isCurValueNull = false;

        public JsonTreeEditor(boolean editable, ModelItem modelItem){
            super(editable, modelItem);
        }

        public void setText(String text){
            isCurValueNull = text == null;
            if(isCurValueNull) text = "";
            setContent(text);
            detectChange();
        }

        public String getText(){
            String result = getXml();
            return "".equals(result) && isCurValueNull ? null : result;
        }

        @Override
        protected void processFocusEvent(FocusEvent event){
            super.processFocusEvent(event);
            if(!event.isTemporary()) detectChange();
        }

        private void detectChange(){
            String newValue = getText();
            if(!Utils.areStringsEqual(prevValue, newValue)){
                firePropertyChange("text", prevValue, newValue);
                prevValue = newValue;
            }
        }

    }

    public static class XmlTreeEditor extends XmlObjectTree {
        private String prevValue = null;

        public XmlTreeEditor(boolean editable, ModelItem modelItem){
            super(editable, modelItem);
        }

        public void setText(String text){
            setContent(text);
            detectChange(text);
        }

        public String getText(){return getXml();}

        @Override
        protected void processFocusEvent(FocusEvent event){
            super.processFocusEvent(event);
            if(!event.isTemporary()) detectChange(getText());
        }

        private void detectChange(String newValue){
            if(!Utils.areStringsEqual(prevValue, newValue)){
                firePropertyChange("text", prevValue, newValue);
                prevValue = newValue;
            }
        }

    }

}

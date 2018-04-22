package burp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import javax.swing.*;

import net.miginfocom.swing.*;
import org.apache.commons.text.StringEscapeUtils;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import javax.swing.JMenuItem;

public class BurpExtender implements IBurpExtender, IMessageEditorTabFactory, ITab, IContextMenuFactory {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    // Data
    private String sourceLang = "vi"; // Vietnamese by default;
    private String destLang = "en";// English by default

    private String[] txtLangscbSource;
    private String[] txtLangscbDest;

    private Properties langNameToKeys;
    private Properties langKeytoNames;

    private String propDir = System.getProperty("user.home") + File.separator + ".BurpSuite" + File.separator;
    private String propFile = "extension-bing-translator.properties";
    private Properties PropData;

    /// GUI ////
    private JPanel mainPane;
    private JPanel panelMain;
    private JLabel lblTitle;
    private JLabel lblNote;
    private JLabel lblSource;
    private JLabel lblDestination;
    private JComboBox<String> cbSource;
    private JComboBox<String> cbDestination;
    private JTextArea txtSource;
    private JTextArea txtDestination;
    private JButton btnTranslate;
    private JButton btnClearAll;

    private JScrollPane jsptxtSource;
    private JScrollPane jsptxtDestination;

    private JTextArea jt;
    private JScrollPane jsp;
    /// END GUI ////

    private Boolean env_testing = false;


    ///// START OF FUNCTIONS

    @Override
    public java.util.List<JMenuItem> createMenuItems(IContextMenuInvocation imv) {
        if (imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST ||
                imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_RESPONSE ||
                imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
                imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_RESPONSE) {

            JMenu mnuBingTranslator = new JMenu("Bing Translator");

            JMenuItem mnuTranslateSelection = new JMenuItem("Translate selected texts");
            mnuTranslateSelection.addActionListener(new mnuTranslateActionListener(imv, "translate_selection"));

            JMenuItem mnuSendSelectionToTranslator = new JMenuItem("Send selected texts to " + getTabCaption());
            mnuSendSelectionToTranslator.addActionListener(new mnuTranslateActionListener(imv, "send_selection"));

            JMenuItem mnuSendAllToTranslator = new JMenuItem("Send all texts to " + getTabCaption());
            mnuSendAllToTranslator.addActionListener(new mnuTranslateActionListener(imv, "send_all"));

            JMenuItem mnuSendOnlyUnicodeToTranslator = new JMenuItem("Send only unicode texts (CN) to " + getTabCaption());
            mnuSendOnlyUnicodeToTranslator.addActionListener(new mnuTranslateActionListener(imv, "send_only_unicode"));

            mnuBingTranslator.add(mnuTranslateSelection);
            mnuBingTranslator.add(mnuSendSelectionToTranslator);
            mnuBingTranslator.add(mnuSendAllToTranslator);
            mnuBingTranslator.add(mnuSendOnlyUnicodeToTranslator);

            java.util.List<JMenuItem> jMenuItems = new ArrayList<JMenuItem>();

            jMenuItems.add(mnuBingTranslator);
            return jMenuItems;
        } else {
            return null;
        }
    }


    private void log(Object data) {
        if (env_testing) {
            System.out.println(data);
        }
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(null,
                ex.toString(),
                "ERROR",
                JOptionPane.ERROR_MESSAGE);
    }

    private void showError(String ex) {
        JOptionPane.showMessageDialog(null,
                ex,
                "ERROR",
                JOptionPane.ERROR_MESSAGE);
    }

    private void showTranslatedContent(String info) {
        jt =  new JTextArea(info);
        jt.setLineWrap(true);
        jt.setWrapStyleWord(true);
        jsp = new JScrollPane(jt) {
            private static final long serialVersionUID = 1L;
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(400, 200);
            }
        };

        JOptionPane.showMessageDialog(null,
                jsp,
                langKeytoNames.get(sourceLang) + " -> " + langKeytoNames.getProperty(destLang),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // load properties file for the first time
    private void checkProp() throws IOException {
        try {
            File dir = new File(propDir);
            if (!dir.exists()) {
                dir.mkdir();
            }
            File file = new File(propDir + File.separator + propFile);
            if (!file.exists()) {
                // save default language settings
                saveProp("sourceLang=vi\r\ndestLang=en");
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void loadProp() {
        try {
            checkProp(); // create default if not exists
            // if prop file already exists
            // read and load it to Properties PropData
            PropData = new Properties();
            PropData.load(new FileInputStream(propDir + File.separator + propFile));
            // get lang setting

            // set lang settings
            sourceLang = PropData.getProperty("sourceLang").trim();
            destLang = PropData.getProperty("destLang").trim();

            // change it to combox
            cbSource.setSelectedItem(langKeytoNames.get(sourceLang));
            cbDestination.setSelectedItem(langKeytoNames.getProperty(destLang));
            log("[INFO] Loaded properties - " + propDir + File.separator + propFile);
            log("[INFO] sourceLang = " + sourceLang);
            log("[INFO] destLang = " + destLang);

        } catch (IOException ioe) {
            showError(ioe);
        }

    }

    private void saveProp(String valuepair) {
        try {
            File dir = new File(propDir);
            if (!dir.exists()) {
                dir.mkdir();
            }
            File file = new File(propDir + File.separator + propFile);
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            out.write(valuepair);
            out.close();
            log("[INFO] Saved propFile with " + valuepair);
        } catch (IOException ioe) {
            showError(ioe);
        }

    }

    //
    // implement IBurpExtender
    //
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        // keep a reference to our callbacks object
        this.callbacks = callbacks;

        // obtain an extension helpers object
        helpers = callbacks.getHelpers();

        // set our extension name
        callbacks.setExtensionName("Bing Translator");

        // register ourselves as a message editor tab factory
        callbacks.registerMessageEditorTabFactory(this);

        // register context menu
        callbacks.registerContextMenuFactory(this);

        // create our UI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                // initialise data

                txtLangscbSource = new String[]{
                        "Vietnamese",
                        "Malay",
                        "Chinese Simplified",
                        "Thai",
                        "Indonesian",
                        "Filipino",
                        "Chinese Traditional",
                        "Cantonese(Traditional)",
                        "English",
                        "Afrikaans",
                        "Arabic",
                        "Bangla",
                        "Bosnian",
                        "Bulgarian",
                        "Catalan",
                        "Croatian",
                        "Czech",
                        "Danish",
                        "Dutch",
                        "Estonian",
                        "Fijian",
                        "Finnish",
                        "French",
                        "German",
                        "Greek",
                        "Haitian",
                        "Creole",
                        "Hebrew",
                        "Hindi",
                        "Hmong",
                        "Daw",
                        "Hungarian",
                        "Italian",
                        "Japanese",
                        "Klingon",
                        "Klingon",
                        "Klingon (plqaD)",
                        "Korean",
                        "Latvian",
                        "Lithuanian",
                        "Malagasy",
                        "Maltese",
                        "Norwegian",
                        "Persian",
                        "Polish",
                        "Portuguese",
                        "Quertaro Otomi",
                        "Romanian",
                        "Russian",
                        "Samoan",
                        "Serbian(Cyrillic)",
                        "Serbian(Latin)",
                        "Slovak",
                        "Slovenian",
                        "Spanish",
                        "Swahili",
                        "Swedish",
                        "Tahitian",
                        "Tamil",
                        "Tongan",
                        "Turkish",
                        "Ukrainian",
                        "Urdu",
                        "Welsh",
                        "Yucatec",
                        "Maya"

                };

                txtLangscbDest = new String[]{
                        "English",
                        "Vietnamese",
                        "Malay",
                        "Chinese Simplified",
                        "Thai",
                        "Indonesian",
                        "Filipino",
                        "Chinese Traditional",
                        "Cantonese(Traditional)",
                        "Afrikaans",
                        "Arabic",
                        "Bangla",
                        "Bosnian",
                        "Bulgarian",
                        "Catalan",
                        "Croatian",
                        "Czech",
                        "Danish",
                        "Dutch",
                        "Estonian",
                        "Fijian",
                        "Finnish",
                        "French",
                        "German",
                        "Greek",
                        "Haitian",
                        "Creole",
                        "Hebrew",
                        "Hindi",
                        "Hmong",
                        "Daw",
                        "Hungarian",
                        "Italian",
                        "Japanese",
                        "Klingon",
                        "Klingon",
                        "Klingon (plqaD)",
                        "Korean",
                        "Latvian",
                        "Lithuanian",
                        "Malagasy",
                        "Maltese",
                        "Norwegian",
                        "Persian",
                        "Polish",
                        "Portuguese",
                        "Quertaro Otomi",
                        "Romanian",
                        "Russian",
                        "Samoan",
                        "Serbian(Cyrillic)",
                        "Serbian(Latin)",
                        "Slovak",
                        "Slovenian",
                        "Spanish",
                        "Swahili",
                        "Swedish",
                        "Tahitian",
                        "Tamil",
                        "Tongan",
                        "Turkish",
                        "Ukrainian",
                        "Urdu",
                        "Welsh",
                        "Yucatec",
                        "Maya"

                };

                langNameToKeys = new Properties();
                langNameToKeys.setProperty("Afrikaans", "af");
                langNameToKeys.setProperty("Arabic", "ar");
                langNameToKeys.setProperty("Bangla", "bn-BD");
                langNameToKeys.setProperty("Bosnian", "bs-Latn");
                langNameToKeys.setProperty("Bulgarian", "bg");
                langNameToKeys.setProperty("Cantonese (Traditional)", "yue");
                langNameToKeys.setProperty("Catalan", "ca");
                langNameToKeys.setProperty("Chinese Simplified", "zh-CHS");
                langNameToKeys.setProperty("Chinese Traditional", "zh-CHT");
                langNameToKeys.setProperty("Croatian", "hr");
                langNameToKeys.setProperty("Czech", "cs");
                langNameToKeys.setProperty("Danish", "da");
                langNameToKeys.setProperty("Dutch", "nl");
                langNameToKeys.setProperty("English", "en");
                langNameToKeys.setProperty("Estonian", "et");
                langNameToKeys.setProperty("Fijian", "fj");
                langNameToKeys.setProperty("Filipino", "fil");
                langNameToKeys.setProperty("Finnish", "fi");
                langNameToKeys.setProperty("French", "fr");
                langNameToKeys.setProperty("German", "de");
                langNameToKeys.setProperty("Greek", "el");
                langNameToKeys.setProperty("Haitian Creole", "ht");
                langNameToKeys.setProperty("Hebrew", "he");
                langNameToKeys.setProperty("Hindi", "hi");
                langNameToKeys.setProperty("Hmong Daw", "mww");
                langNameToKeys.setProperty("Hungarian", "hu");
                langNameToKeys.setProperty("Indonesian", "id");
                langNameToKeys.setProperty("Italian", "it");
                langNameToKeys.setProperty("Japanese", "ja");
                langNameToKeys.setProperty("Klingon", "tlh");
                langNameToKeys.setProperty("Klingon (plqaD)", "tlh-Qaak");
                langNameToKeys.setProperty("Korean", "ko");
                langNameToKeys.setProperty("Latvian", "lv");
                langNameToKeys.setProperty("Lithuanian", "lt");
                langNameToKeys.setProperty("Malagasy", "mg");
                langNameToKeys.setProperty("Malay", "ms");
                langNameToKeys.setProperty("Maltese", "mt");
                langNameToKeys.setProperty("Norwegian", "no");
                langNameToKeys.setProperty("Persian", "fa");
                langNameToKeys.setProperty("Polish", "pl");
                langNameToKeys.setProperty("Portuguese", "pt");
                langNameToKeys.setProperty("Quertaro Otomi", "otq");
                langNameToKeys.setProperty("Romanian", "ro");
                langNameToKeys.setProperty("Russian", "ru");
                langNameToKeys.setProperty("Samoan", "sm");
                langNameToKeys.setProperty("Serbian (Cyrillic)", "sr-Cyrl");
                langNameToKeys.setProperty("Serbian (Latin)", "sr-Latn");
                langNameToKeys.setProperty("Slovak", "sk");
                langNameToKeys.setProperty("Slovenian", "sl");
                langNameToKeys.setProperty("Spanish", "es");
                langNameToKeys.setProperty("Swahili", "sw");
                langNameToKeys.setProperty("Swedish", "sv");
                langNameToKeys.setProperty("Tahitian", "ty");
                langNameToKeys.setProperty("Tamil", "ta");
                langNameToKeys.setProperty("Thai", "th");
                langNameToKeys.setProperty("Tongan", "to");
                langNameToKeys.setProperty("Turkish", "tr");
                langNameToKeys.setProperty("Ukrainian", "uk");
                langNameToKeys.setProperty("Urdu", "ur");
                langNameToKeys.setProperty("Vietnamese", "vi");
                langNameToKeys.setProperty("Welsh", "cy");
                langNameToKeys.setProperty("Yucatec Maya", "yua");

                langKeytoNames = new Properties();
                langKeytoNames.setProperty("af", "Afrikaans");
                langKeytoNames.setProperty("ar", "Arabic");
                langKeytoNames.setProperty("bn-BD", "Bangla");
                langKeytoNames.setProperty("bs-Latn", "Bosnian");
                langKeytoNames.setProperty("bg", "Bulgarian");
                langKeytoNames.setProperty("yue", "Cantonese (Traditional)");
                langKeytoNames.setProperty("ca", "Catalan");
                langKeytoNames.setProperty("zh-CHS", "Chinese Simplified");
                langKeytoNames.setProperty("zh-CHT", "Chinese Traditional");
                langKeytoNames.setProperty("hr", "Croatian");
                langKeytoNames.setProperty("cs", "Czech");
                langKeytoNames.setProperty("da", "Danish");
                langKeytoNames.setProperty("nl", "Dutch");
                langKeytoNames.setProperty("en", "English");
                langKeytoNames.setProperty("et", "Estonian");
                langKeytoNames.setProperty("fj", "Fijian");
                langKeytoNames.setProperty("fil", "Filipino");
                langKeytoNames.setProperty("fi", "Finnish");
                langKeytoNames.setProperty("fr", "French");
                langKeytoNames.setProperty("de", "German");
                langKeytoNames.setProperty("el", "Greek");
                langKeytoNames.setProperty("ht", "Haitian Creole");
                langKeytoNames.setProperty("he", "Hebrew");
                langKeytoNames.setProperty("hi", "Hindi");
                langKeytoNames.setProperty("mww", "Hmong Daw");
                langKeytoNames.setProperty("hu", "Hungarian");
                langKeytoNames.setProperty("id", "Indonesian");
                langKeytoNames.setProperty("it", "Italian");
                langKeytoNames.setProperty("ja", "Japanese");
                langKeytoNames.setProperty("tlh", "Klingon");
                langKeytoNames.setProperty("tlh-Qaak", "Klingon (plqaD)");
                langKeytoNames.setProperty("ko", "Korean");
                langKeytoNames.setProperty("lv", "Latvian");
                langKeytoNames.setProperty("lt", "Lithuanian");
                langKeytoNames.setProperty("mg", "Malagasy");
                langKeytoNames.setProperty("ms", "Malay");
                langKeytoNames.setProperty("mt", "Maltese");
                langKeytoNames.setProperty("no", "Norwegian");
                langKeytoNames.setProperty("fa", "Persian");
                langKeytoNames.setProperty("pl", "Polish");
                langKeytoNames.setProperty("pt", "Portuguese");
                langKeytoNames.setProperty("otq", "Quertaro Otomi");
                langKeytoNames.setProperty("ro", "Romanian");
                langKeytoNames.setProperty("ru", "Russian");
                langKeytoNames.setProperty("sm", "Samoan");
                langKeytoNames.setProperty("sr-Cyrl", "Serbian (Cyrillic)");
                langKeytoNames.setProperty("sr-Latn", "Serbian (Latin)");
                langKeytoNames.setProperty("sk", "Slovak");
                langKeytoNames.setProperty("sl", "Slovenian");
                langKeytoNames.setProperty("es", "Spanish");
                langKeytoNames.setProperty("sw", "Swahili");
                langKeytoNames.setProperty("sv", "Swedish");
                langKeytoNames.setProperty("ty", "Tahitian");
                langKeytoNames.setProperty("ta", "Tamil");
                langKeytoNames.setProperty("th", "Thai");
                langKeytoNames.setProperty("to", "Tongan");
                langKeytoNames.setProperty("tr", "Turkish");
                langKeytoNames.setProperty("uk", "Ukrainian");
                langKeytoNames.setProperty("ur", "Urdu");
                langKeytoNames.setProperty("vi", "Vietnamese");
                langKeytoNames.setProperty("cy", "Welsh");
                langKeytoNames.setProperty("yua", "Yucatec Maya");

                ////////////////////////////////////////
                /////  GUI
                ////////////////////////////////////////

                //Main split pane
                mainPane = new JPanel(new BorderLayout()); // mandatory to wrap MigLayout


                panelMain = new JPanel();
                lblTitle = new JLabel();
                lblNote = new JLabel();
                lblSource = new JLabel();
                lblDestination = new JLabel();


                cbSource = new JComboBox<String>(txtLangscbSource);
                cbDestination = new JComboBox<String>(txtLangscbDest);

                txtSource = new JTextArea();
                txtDestination = new JTextArea();
                btnTranslate = new JButton();
                btnClearAll = new JButton();

                //======== panelMain ========
                {
                    panelMain.setLayout(new MigLayout(
                            "hidemode 3",
                            // columns
                            "[fill]" +
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]" +
                                    "[fill]",
                            // rows
                            "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]" +
                                    "[]"));

                    //---- lblTitle ----
                    lblTitle.setText("Bing Translator by Myo Soe - https://yehg.net");
                    panelMain.add(lblTitle, "cell 0 0");

                    //---- lblNote ----
                    lblNote.setText("Note: The language setting below affects suite wide.");
                    panelMain.add(lblNote, "cell 0 1");

                    //---- lblSource ----
                    lblSource.setText("Source");
                    panelMain.add(lblSource, "cell 0 2");

                    //---- lblDestination ----
                    lblDestination.setText("Destination");
                    panelMain.add(lblDestination, "cell 1 2");
                    panelMain.add(cbSource, "cell 0 3");
                    panelMain.add(cbDestination, "cell 1 3");

                    //---- txtSource ----
                    txtSource.setRows(10);
                    txtSource.setWrapStyleWord(true);
                    txtSource.setColumns(20);
                    txtSource.setLineWrap(true);
                    //panelMain.add(txtSource, "cell 0 5,wmin 500,height 300");

                    //---- txtDestination ----
                    txtDestination.setRows(10);
                    txtDestination.setWrapStyleWord(true);
                    txtDestination.setColumns(20);
                    txtDestination.setLineWrap(true);
                    //panelMain.add(txtDestination, "cell 1 5,wmin 500,height 300");

                    //---- btnTranslate ----
                    btnTranslate.setText("Translate");
                    panelMain.add(btnTranslate, "cell 0 7");

                    //---- btnClearAll ----
                    btnClearAll.setText("Clear All");
                    panelMain.add(btnClearAll, "cell 1 7");

                    // ---  scrollPane ---

                    jsptxtSource = new JScrollPane();
                    jsptxtSource.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                    jsptxtSource.setViewportView(txtSource);
                    panelMain.add(jsptxtSource, "cell 0 5,wmin 500,height 300");

                    jsptxtDestination = new JScrollPane();
                    jsptxtDestination.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                    jsptxtDestination.setViewportView(txtDestination);
                    panelMain.add(jsptxtDestination, "cell 1 5,wmin 500,height 300");

                }


                // END OF mainPane

                /////// Action Listener

                cbSource.addActionListener(new cbSourceComboActionListener());
                cbDestination.addActionListener(new cbDestinationComboActionListener());
                btnTranslate.addActionListener(new translateButtonActionListener());
                btnClearAll.addActionListener(new clearAllButtonActionListener());


                /////// END Of Action Listener

                mainPane.add(panelMain);

                // Load setting from properties and will change combo selection accordingly
                loadProp();


                callbacks.customizeUiComponent(panelMain);

                // Add the custom tab to Burp's UI
                callbacks.addSuiteTab(BurpExtender.this);


                log("[INFO] Bing Translator loaded");


            }
        });
    }

    public void selectTab() {
        Component current = this.getUiComponent();
        do { //Go Up Heirarchy to find jTabbedPane
            current = current.getParent();
        } while (!(current instanceof JTabbedPane));

        JTabbedPane tabPane = (JTabbedPane) current;
        for (int i = 0; i < tabPane.getTabCount(); i++) {
            //Find the TabbedPane with the Caption That matches this caption
            // and select it.
            if (tabPane.getTitleAt(i).equals(this.getTabCaption()))
                tabPane.setSelectedIndex(i);
        }
    }

    class mnuTranslateActionListener implements ActionListener {

        private static final long serialVersionUID = 1L;
        private IContextMenuInvocation imv;
        private String option = "all";


        public mnuTranslateActionListener(IContextMenuInvocation imv, String option) {
            this.imv = imv;
            this.option = option;

        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            try {
                log("[INFO] mnuTranslate triggered with " + this.option);
                log("imv.getSelectionBounds().length " + Integer.toString(imv.getSelectionBounds().length));

                int start = imv.getSelectionBounds()[0];
                int stop = imv.getSelectionBounds()[1];
                switch (this.option) {
                    case "send_selection":

                        for (IHttpRequestResponse httpRequestResponse : imv.getSelectedMessages()) {

                            String wholeRequestResponse = getMessage(httpRequestResponse);

                            String selectedText = wholeRequestResponse.substring(start, stop);
                            log("[INFO] selected: " + selectedText);
                            log("[INFO] selectedText.length: " + selectedText.length());
                            if (selectedText.length() != 0) {

                                txtSource.setText(selectedText);
                                selectTab();
                            } else {
                                showError("Select your desired text for translation.");
                            }
                            break;
                        }
                        break;

                    case "translate_selection":
                        for (IHttpRequestResponse httpRequestResponse : imv.getSelectedMessages()) {
                            String wholeRequestResponse = getMessage(httpRequestResponse);

                            String selectedText = wholeRequestResponse.substring(start, stop);

                            if (selectedText.length() != 0) {
                                log("[INFO] selected: " + selectedText);
                                if (selectedText.contains("\\u")) {
                                    selectedText = StringEscapeUtils.unescapeJava(selectedText);
                                }
                                String result = translateUserAction(selectedText);
                                showTranslatedContent(result);
                            } else {
                                showError("Select your desired text for translation.");
                            }
                            break;
                        }
                        break;
                    case "send_all":
                        for (IHttpRequestResponse httpRequestResponse : imv.getSelectedMessages()) {
                            String wholeRequestResponse = getMessage(httpRequestResponse);
                            txtSource.setText(wholeRequestResponse);
                            selectTab();
                            break;
                        }
                        break;
                    case "send_only_unicode":
                        for (IHttpRequestResponse httpRequestResponse : imv.getSelectedMessages()) {
                            String wholeRequestResponse = getMessage(httpRequestResponse);
                            // convert to their representative form
                            if (wholeRequestResponse.contains("\\u")) {
                                wholeRequestResponse = StringEscapeUtils.unescapeJava(wholeRequestResponse);
                            }
                            // remove ascii
                            wholeRequestResponse = wholeRequestResponse.replaceAll("[^\\n\\r\\t\\P{Print}]", "");

                            // trim
                            wholeRequestResponse = wholeRequestResponse.trim();

                            // replace 2 new lines into for compactness
                            for (int x=0; x< 3;x++ ){
                                wholeRequestResponse = wholeRequestResponse.replaceAll("\r\n\r\n","\r\n");
                                wholeRequestResponse = wholeRequestResponse.replaceAll("\n\n","\n");
                            }

                            txtSource.setText(wholeRequestResponse);
                            selectTab();
                            break;
                        }
                        break;
                }


            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private boolean isRequest() {
            if (imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST || imv.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST)
                return true;
            else
                return false;
        }

        private String getMessage(IHttpRequestResponse httpRequestResponse) {
            return (new String(isRequest() ? httpRequestResponse.getRequest() : httpRequestResponse.getResponse()));
        }

        private byte[] getMsgBytes(IHttpRequestResponse httpRequestResponse) {
            return isRequest() ? httpRequestResponse.getRequest() : httpRequestResponse.getResponse();
        }

        private void setMessage(IHttpRequestResponse httpRequestResponse, String update) {
            if (isRequest()) {
                httpRequestResponse.setRequest(update.getBytes());

            } else {
                httpRequestResponse.setResponse(update.getBytes());
            }
        }

        private void setMsgBytes(IHttpRequestResponse httpRequestResponse, byte[] update) {
            if (isRequest()) {
                httpRequestResponse.setRequest(update);

            } else {
                httpRequestResponse.setResponse(update);
            }
        }
    }

    class cbSourceComboActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            try {
                if (cbSource.getSelectedItem() != null) {
                    log("\r\nSource Lang Selected: " + cbSource.getSelectedItem().toString());
                    log("\r\nSource Lang Key : " + langNameToKeys.getProperty(cbSource.getSelectedItem().toString()));
                    sourceLang = langNameToKeys.getProperty(cbSource.getSelectedItem().toString());
                    log("\r\nDefault sourceLang is now " + sourceLang);
                    // destLang not changed yet; will use previous one
                    saveProp("sourceLang=" + sourceLang + "\r\ndestLang=" + destLang);

                }
            } catch (Exception ex) {
                showError(ex);
            }
        }
    }

    class cbDestinationComboActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            try {
                if (cbDestination.getSelectedItem() != null) {
                    log("\r\nDest Lang Selected: " + cbDestination.getSelectedItem().toString());
                    log("\r\nDest Lang Key : " + langNameToKeys.getProperty(cbDestination.getSelectedItem().toString()));
                    destLang = langNameToKeys.getProperty(cbDestination.getSelectedItem().toString());
                    log("\r\nDefault destLang is now " + destLang);
                    // sourceLang not changed yet; will use previous one
                    saveProp("sourceLang=" + sourceLang + "\r\ndestLang=" + destLang);
                }
            } catch (Exception ex) {
                showError(ex);
            }
        }
    }

    class clearAllButtonActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            try {
                txtDestination.setText("");
                txtSource.setText("");
            } catch (Exception ex) {
                showError(ex);
            }
        }
    }

    private String translateUserAction(String tmp) {
        byte[] bytes = tmp.getBytes(StandardCharsets.UTF_8);
        String rbody = new String(bytes, StandardCharsets.UTF_8);

        if (rbody.contains("\\u")) {
            rbody = StringEscapeUtils.unescapeJava(rbody);
        }
        //rbody = helpers.urlEncode(rbody);

        //log("[btnTranslate encode using burp helper] " + rbody);

        log("\r\n[btnTranslate - Data to send to Translator] " + rbody);

        String result = TranslatorAPI.getTranslatedContent_URLencoded(rbody, false, sourceLang, destLang);

        result = TranslatorAPI.bclean(result, false);
        return result;
    }

    //Listener for when the "Beautify" button is clicked
    class translateButtonActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            try {

                // clear txtDes
                txtDestination.setText("");

                // send original
                String tmp = txtSource.getText();
                String result = translateUserAction(tmp);

                // line break if results contain \n
                if (result.contains("\\n")) {
                    log("[INFO] result contains new line");
                    Scanner sc = new Scanner(result);
                    Boolean firstLine = true;
                    while (sc.hasNextLine()) {
                        String nextChar = sc.next();
                        if (nextChar.length() > 0) { // you could even use Java regexes to validate the format of every line
                            log("nextChar: " + nextChar);
                            if (nextChar.contains("\\n")) {
                                log("[INFO] nextChar contains new line");
                                String tmp1 = nextChar;
                                tmp1 = tmp1.replace("\\r\\n", "\r\n");
                                txtDestination.append(tmp1);
                            } else {
                                if (firstLine == true) {
                                    txtDestination.append(nextChar + " ");
                                    firstLine = false;
                                } else {
                                    txtDestination.append(" " + nextChar + " ");
                                }

                            }

                        }
                    }

                } else {
                    log("[INFO] result DOES NOT contain new line");
                    txtDestination.setText(result);
                }


            } catch (Exception ex) {
                showError(ex);
            }
        }
    }

    //
    // implement IMessageEditorTabFactory
    //
    @Override
    public IMessageEditorTab createNewInstance(IMessageEditorController controller, boolean editable) {
        // create a new instance of our custom beautifer tab
        return new TranslatorTab(controller, editable);
    }

    @Override
    public String getTabCaption() {
        return "Bing Translator";
    }

    @Override
    public Component getUiComponent() {
        return mainPane;
    }


    //
    // class implementing IMessageEditorTab
    //
    class TranslatorTab implements IMessageEditorTab {

        private static final long serialVersionUID = 1L;

        private boolean editable;
        private ITextEditor txtInput;
        private byte[] currentMessage;
        private boolean modifiedJSON = false;

        public TranslatorTab(IMessageEditorController controller, boolean editable) {
            this.editable = editable;

            // create an instance of Burp's text editor, to display our deserialized data
            txtInput = callbacks.createTextEditor();
            txtInput.setEditable(editable);
        }

        //
        // implement IMessageEditorTab
        //
        @Override
        public String getTabCaption() {
            return "Bing Translator";
        }

        @Override
        public Component getUiComponent() {
            return txtInput.getComponent();
        }

        @Override
        public boolean isEnabled(byte[] content, boolean isRequest) {
            IRequestInfo requestInfo;
            IResponseInfo responseInfo;
            if (isRequest) {
                requestInfo = helpers.analyzeRequest(content);
                return requestInfo.getContentType() == IRequestInfo.CONTENT_TYPE_JSON;
            } else {
                responseInfo = helpers.analyzeResponse(content);

                //log("[INFO] responseInfo.getHeaders().toString() " + responseInfo.getHeaders().toString());

                if (responseInfo.getHeaders().toString().contains("application/json")) {
                    return true;
                } else {
                    return responseInfo.getInferredMimeType().equals("JSON");
                }


            }
        }

        @Override
        public void setMessage(byte[] content, boolean isRequest) {
            String json = "";
            if (content == null) {
                // clear our display
                txtInput.setText(" ".getBytes());
                txtInput.setEditable(false);
            } else {
                //Take the input, determine request/response, parse as json, then print prettily.
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
                int bodyOffset = 0;
                if (isRequest) {
                    // skip request
                    txtInput.setText("Not supported here".getBytes());
                    txtInput.setEditable(false);
                } else {
                    IResponseInfo responseInfo = helpers.analyzeResponse(content);
                    bodyOffset = responseInfo.getBodyOffset();

                    //Get only the JSON part of the content
                    byte[] requestResponseBody = Arrays.copyOfRange(content, bodyOffset, content.length);
                    try {

                        // send original
                        String rbody = new String(requestResponseBody);

                        log("\r\n[Data to send to Translator] " + rbody);

                        String result = TranslatorAPI.getTranslatedContent_URLencoded(rbody, true, sourceLang, destLang);

                        result = TranslatorAPI.bclean(result, true);

                        //result =  TranslatorAPI.beautifyjson(result);

                        result = "Bing Translator (from: " + langKeytoNames.get(sourceLang) + " -> to: " + langKeytoNames.getProperty(destLang) + ")\r\n\r\n" + result;
                        txtInput.setText(result.getBytes());
                        txtInput.setEditable(editable);

                        modifiedJSON = true;

                    } catch (Exception e) {

                        txtInput.setText(e.toString().getBytes());
                    }


                }


            }

            // remember the displayed content
            currentMessage = content;
        }

        @Override
        public byte[] getMessage() {
            String json = "";
            //Get the modified content and add the headers back to the top
            if (txtInput.isTextModified()) {
                Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
                try {
                    JsonParser jp = new JsonParser();
                    JsonElement je = jp.parse(new String(txtInput.getText()));
                    json = gson.toJson(je);
                    IRequestInfo requestInfo = helpers.analyzeRequest(currentMessage);
                    return helpers.buildHttpMessage(requestInfo.getHeaders(), json.getBytes());
                } catch (Exception e) {
                    return currentMessage;
                }
            }
            return null;
        }

        @Override
        public boolean isModified() {
            return txtInput.isTextModified();
        }

        @Override
        public byte[] getSelectedData() {
            return txtInput.getSelectedText();
        }
    }
}
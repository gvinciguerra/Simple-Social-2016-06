/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client.gui;

import client.AuthenticationManager;
import client.Client;
import client.ResponseException;
import client.ShortConnectionFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.rmi.NotBoundException;

import static server.Server.SERVER_PORT;

public class LoginForm {

    private ShortConnectionFactory factory;
    private AuthenticationManager authenticationManager;
    private Client client;
    private JPanel panel1;
    private JButton loginButton;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton registerButton;
    private JButton settingsButton;

    public static void showAlert(String message) {
        JOptionPane.showMessageDialog(null, message);
    }

    public LoginForm() {
        usernameField.setDocument(new JTextFieldLimit(20));
        passwordField.setDocument(new JTextFieldLimit(30));
        try {
            factory = new ShortConnectionFactory("localhost", SERVER_PORT);
        } catch (Exception e) {

        }

        Icon icon = UIManager.getIcon("FileView.fileIcon");
        settingsButton.setIcon(icon);
        settingsButton.addActionListener(o -> {
            JFrame frame = new JFrame();
            String hostname = JOptionPane.showInputDialog(frame, "Server hostname", "localhost");
            if (hostname != null) {
                try {
                    factory = new ShortConnectionFactory(hostname, SERVER_PORT);
                } catch (Exception e) {
                    showAlert("Error: " + e.getMessage());
                }
            }
        });

        loginButton.addActionListener(o -> {
            if (usernameField.getText().isEmpty()) {
                showAlert("Username can't be empty");
                return;
            }
            try {
                String password = new String(passwordField.getPassword());
                authenticationManager = new AuthenticationManager(factory, usernameField.getText(), password);
                client = new Client(authenticationManager);

                JFrame loginFrame = (JFrame) SwingUtilities.getWindowAncestor(panel1);
                MainForm mainForm = new MainForm(client, loginFrame);
                JFrame frame = new JFrame(authenticationManager.getUsername() + "'s Simple-Social");
                frame.setContentPane(mainForm.panel1);
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        mainForm.savePendingFriendRequests();
                        client.close();
                        System.exit(0);
                    }
                });
                frame.setMinimumSize(new Dimension(550, 341));
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                loginFrame.setVisible(false);
            } catch (ResponseException e) {
                showAlert("Username or password is incorrect");
            } catch (IOException | NotBoundException e) {
                showAlert("Connection error: " + e.getMessage());
            }
        });

        registerButton.addActionListener(o -> {
            if (usernameField.getText().isEmpty()) {
                showAlert("Username can't be empty");
                return;
            }
            registerButton.setEnabled(false);
            try {
                String password = new String(passwordField.getPassword());
                AuthenticationManager.register(factory, usernameField.getText(), password);
            } catch (IOException | ResponseException e) {
                showAlert("Error: " + e.getMessage());
                registerButton.setEnabled(true);
            }
        });
        usernameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                registerButton.setEnabled(true);
            }
        });
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(4, 3, new Insets(10, 10, 10, 10), -1, -1));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        loginButton = new JButton();
        loginButton.setText("Login");
        panel1.add(loginButton, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        usernameField = new JTextField();
        panel1.add(usernameField, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Username:");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        passwordField = new JPasswordField();
        panel1.add(passwordField, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Password:");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        registerButton = new JButton();
        registerButton.setText("Register");
        panel1.add(registerButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        settingsButton = new JButton();
        settingsButton.setBorderPainted(true);
        settingsButton.setContentAreaFilled(true);
        settingsButton.setFocusPainted(false);
        settingsButton.setFocusable(false);
        settingsButton.setOpaque(false);
        settingsButton.setRequestFocusEnabled(false);
        settingsButton.setText("");
        panel1.add(settingsButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }

    public static class JTextFieldLimit extends PlainDocument {
        private int limit;

        JTextFieldLimit(int limit) {
            super();
            this.limit = limit;
        }

        public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
            if (str == null)
                return;

            if ((getLength() + str.length()) <= limit) {
                super.insertString(offset, str, attr);
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        JFrame frame = new JFrame("Simple-Social Login");
        frame.setContentPane(new LoginForm().panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


}

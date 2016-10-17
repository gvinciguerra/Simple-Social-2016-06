/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client.gui;

import client.Client;
import client.ClientEventListener;
import client.ResponseException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

public class MainForm {

    private JTextArea feedTextArea;
    JPanel panel1;
    private JTabbedPane tabbedPane1;
    private JList searchList;
    private JTextField searchField;
    private JButton searchButton;
    private JButton addFriendButton;
    private JTextField addFriendField;
    private JList friendsList;
    private JButton publishButton;
    private JTextField publishField;
    private JButton refreshFriendsButton;
    private JList requestsList;
    private JButton refreshRequestsButton;
    private JButton acceptButton;
    private JButton denyButton;
    private JButton followButton;
    private JButton refreshFeedButton;
    private JButton logoutButton;
    private Client client;
    private ArrayList<String> requests = new ArrayList<>();

    public MainForm(Client c, JFrame loginFrame) {
        this.client = c;

        publishField.setDocument(new LoginForm.JTextFieldLimit(500));
        searchField.setDocument(new LoginForm.JTextFieldLimit(20));
        addFriendField.setDocument(new LoginForm.JTextFieldLimit(20));

        publishButton.addActionListener(a -> {
            String message = publishField.getText().trim();
            if (message.isEmpty()) {
                LoginForm.showAlert("Empty message");
                return;
            }
            publishButton.setEnabled(false);
            try {
                client.publish(message);
                publishField.setText("");
                feedTextArea.append("ME: " + message + "\n\n");
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            } finally {
                publishButton.setEnabled(true);
            }
        });

        searchButton.addActionListener(a -> {
            if (searchField.getText().isEmpty()) {
                LoginForm.showAlert("Empty query");
                return;
            }
            try {
                searchList.setListData(client.findUsers(searchField.getText()).toArray());
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });


        DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();
        ListCellRenderer cellRenderer = ((list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index, isSelected,
                    cellHasFocus);
            lbl.setOpaque(isSelected);
            return lbl;
        });
        searchList.setCellRenderer(cellRenderer);
        requestsList.setCellRenderer(cellRenderer);

        Color onlineColor = new Color(50, 100, 49);
        Color offlineColor = new Color(130, 51, 42);
        friendsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            Client.FriendWithStatus f = (Client.FriendWithStatus) value;
            JLabel label = (JLabel) defaultRenderer.getListCellRendererComponent(list, f.getUsername(), index, isSelected,
                    cellHasFocus);
            if (!isSelected)
                label.setForeground(f.isOnline() ? onlineColor : offlineColor);
            label.setText((f.isOnline() ? "◉" : "◎") + label.getText());
            label.setOpaque(isSelected);
            return label;
        });

        refreshRequestsButton.addActionListener(a -> {
            java.util.List<String> newRequests = client.retrievePendingFriendRequests();
            newRequests.stream().filter(r -> !requests.contains(r)).forEach(r -> requests.add(r));
            requestsList.setListData(requests.toArray());
        });

        refreshFriendsButton.addActionListener(a -> {
            try {
                friendsList.setListData(client.retrieveFriends().toArray());
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });

        addFriendButton.addActionListener(a -> {
            if (addFriendField.getText().isEmpty()) {
                LoginForm.showAlert("Empty username");
                return;
            }
            try {
                client.friendRequest(addFriendField.getText());
                LoginForm.showAlert("Request sent");
                addFriendField.setText("");
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });

        acceptButton.addActionListener(a -> {
            String username = (String) requestsList.getSelectedValue();
            if (username == null)
                return;
            try {
                client.respondFriendRequest(username, true);
                requests.remove(username);
                refreshRequestsButton.doClick();
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });

        denyButton.addActionListener(a -> {
            String username = (String) requestsList.getSelectedValue();
            if (username == null)
                return;
            try {
                client.respondFriendRequest(username, false);
                requests.remove(username);
                refreshRequestsButton.doClick();
            } catch (IOException | ResponseException e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });

        followButton.addActionListener(a -> {
            String username = ((Client.FriendWithStatus) friendsList.getSelectedValue()).getUsername();
            if (username == null)
                return;
            try {
                client.subscribe(username);
            } catch (Exception e) {
                LoginForm.showAlert("Error: " + e.getMessage());
            }
        });

        refreshFeedButton.addActionListener(a -> {
            java.util.List<Client.PostWithAuthor> unreadPosts = client.retrieveUnreadPosts();
            for (Client.PostWithAuthor post : unreadPosts) {
                feedTextArea.append(post.getAuthor() + ": " + post.getContent() + "\n\n");
            }
        });

        searchField.addActionListener(a -> searchButton.doClick());
        publishField.addActionListener(a -> publishButton.doClick());
        addFriendField.addActionListener(a -> addFriendButton.doClick());
        friendsList.addListSelectionListener(s -> followButton.setEnabled(!friendsList.isSelectionEmpty()));
        requestsList.addListSelectionListener(s -> {
            acceptButton.setEnabled(!requestsList.isSelectionEmpty());
            denyButton.setEnabled(!requestsList.isSelectionEmpty());
        });
        tabbedPane1.addChangeListener(e -> {
            if (tabbedPane1.getSelectedIndex() == 1)
                refreshFriendsButton.doClick(0);
        });

        logoutButton.addActionListener(e -> {
            client.close();
            loginFrame.setVisible(true);
            SwingUtilities.getWindowAncestor(panel1).setVisible(false);
            savePendingFriendRequests();
        });

        client.setClientEventListener(new ClientEventListener() {
            @Override
            public void friendRequestReceived() {
                refreshRequestsButton.doClick(0);
            }

            @Override
            public void friendPostReceived() {
                refreshFeedButton.doClick(0);
            }
        });

        loadPendingFriendRequests();
        refreshFriendsButton.doClick(0);
        refreshRequestsButton.doClick(0);
        refreshFeedButton.doClick(0);
    }

    private String getPendingFriendRequestsBackupPath() {
        return client.getUsername().replaceAll("\\W+", "") + ".ssbk";
    }

    private void loadPendingFriendRequests() {
        try {
            FileInputStream fin = new FileInputStream(getPendingFriendRequestsBackupPath());
            ObjectInputStream oin = new ObjectInputStream(fin);
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) oin.readObject();
            requests.addAll(list);
        } catch (Exception e) {

        }
    }

    public void savePendingFriendRequests() {
        refreshRequestsButton.doClick();
        try {
            FileOutputStream fout = new FileOutputStream(getPendingFriendRequestsBackupPath());
            ObjectOutputStream oout = new ObjectOutputStream(fout);
            oout.writeObject(requests);
        } catch (IOException e) {

        }
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
        panel1.setLayout(new GridLayoutManager(3, 4, new Insets(10, 10, 10, 10), -1, -1));
        panel1.setMinimumSize(new Dimension(550, 341));
        tabbedPane1 = new JTabbedPane();
        tabbedPane1.setFont(new Font(tabbedPane1.getFont().getName(), tabbedPane1.getFont().getStyle(), 10));
        panel1.add(tabbedPane1, new GridConstraints(0, 3, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), new Dimension(350, -1), 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.setOpaque(false);
        tabbedPane1.addTab("Search", panel2);
        searchList = new JList();
        searchList.setOpaque(false);
        panel2.add(searchList, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        searchField = new JTextField();
        panel2.add(searchField, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        searchButton = new JButton();
        searchButton.setFont(new Font(searchButton.getFont().getName(), searchButton.getFont().getStyle(), 10));
        searchButton.setText("Search");
        panel2.add(searchButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel3.setOpaque(false);
        tabbedPane1.addTab("Friends", panel3);
        followButton = new JButton();
        followButton.setEnabled(false);
        followButton.setFont(new Font(followButton.getFont().getName(), followButton.getFont().getStyle(), 10));
        followButton.setText("Follow");
        panel3.add(followButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addFriendField = new JTextField();
        panel3.add(addFriendField, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(172, 24), null, 0, false));
        addFriendButton = new JButton();
        addFriendButton.setFont(new Font(addFriendButton.getFont().getName(), addFriendButton.getFont().getStyle(), 10));
        addFriendButton.setText("Add");
        panel3.add(addFriendButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        friendsList = new JList();
        friendsList.setEnabled(true);
        friendsList.setFocusable(false);
        friendsList.setOpaque(false);
        panel3.add(friendsList, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        refreshFriendsButton = new JButton();
        refreshFriendsButton.setFont(new Font(refreshFriendsButton.getFont().getName(), refreshFriendsButton.getFont().getStyle(), 10));
        refreshFriendsButton.setText("Refresh");
        panel3.add(refreshFriendsButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel4.setOpaque(false);
        tabbedPane1.addTab("Requests", panel4);
        requestsList = new JList();
        requestsList.setOpaque(false);
        panel4.add(requestsList, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        refreshRequestsButton = new JButton();
        refreshRequestsButton.setFont(new Font(refreshRequestsButton.getFont().getName(), refreshRequestsButton.getFont().getStyle(), 10));
        refreshRequestsButton.setText("Refresh");
        panel4.add(refreshRequestsButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        acceptButton = new JButton();
        acceptButton.setEnabled(false);
        acceptButton.setFont(new Font(acceptButton.getFont().getName(), acceptButton.getFont().getStyle(), 10));
        acceptButton.setText("Accept");
        panel4.add(acceptButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        denyButton = new JButton();
        denyButton.setEnabled(false);
        denyButton.setFont(new Font(denyButton.getFont().getName(), denyButton.getFont().getStyle(), 10));
        denyButton.setText("Deny");
        panel4.add(denyButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setFont(new Font(label1.getFont().getName(), Font.BOLD, label1.getFont().getSize()));
        label1.setText("Feed of posts");
        panel1.add(label1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        feedTextArea = new JTextArea();
        feedTextArea.setEditable(false);
        feedTextArea.setLineWrap(true);
        scrollPane1.setViewportView(feedTextArea);
        publishButton = new JButton();
        publishButton.setFont(new Font(publishButton.getFont().getName(), publishButton.getFont().getStyle(), 10));
        publishButton.setForeground(new Color(-13991237));
        publishButton.setText("Publish");
        panel1.add(publishButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        publishField = new JTextField();
        panel1.add(publishField, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        refreshFeedButton = new JButton();
        refreshFeedButton.setFont(new Font(refreshFeedButton.getFont().getName(), refreshFeedButton.getFont().getStyle(), 10));
        refreshFeedButton.setText("Refresh");
        panel1.add(refreshFeedButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        logoutButton = new JButton();
        logoutButton.setFont(new Font(logoutButton.getFont().getName(), logoutButton.getFont().getStyle(), 10));
        logoutButton.setText("◀ Logout");
        panel1.add(logoutButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }
}

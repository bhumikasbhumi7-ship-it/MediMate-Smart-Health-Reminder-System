import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * MediMate - Smart Health Reminder System
 * Single-file Java Swing demo. Requires Java 8+ and no external libraries.
 *
 * Run:
 *   javac MediMate.java
 *   java MediMate
 *
 * Data files are created in the folder from which the program is launched.
 */
public class MediMate extends JFrame {
    private static final String USERS_FILE = "medimate_users.txt";
    private static final String MEDICINES_FILE = "medimate_medicines.txt";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final String currentUser;
    private final List<Medicine> medicines = new ArrayList<>();
    private final Set<String> alertsShown = new HashSet<>();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Medicine", "Dosage", "Reminder", "Today's status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable medicineTable = new JTable(tableModel);
    private final JLabel welcomeLabel = new JLabel();

    private MediMate(String username) {
        super("MediMate - Smart Health Reminder System");
        this.currentUser = username;

        loadMedicines();
        buildDashboard();
        refreshTable();

        // Check reminder times every 20 seconds while this application is open.
        new javax.swing.Timer(20_000, e -> checkReminders()).start();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 540);
        setMinimumSize(new Dimension(720, 450));
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);

            if (login.loggedInUser != null) {
                new MediMate(login.loggedInUser).setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }

    private void buildDashboard() {
        setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("MediMate", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));

        welcomeLabel.setText("Welcome, " + currentUser
                + " | Manage your daily medicine reminders");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
        header.setBorder(BorderFactory.createEmptyBorder(14, 12, 8, 12));
        header.add(title);
        header.add(welcomeLabel);
        add(header, BorderLayout.NORTH);

        medicineTable.setRowHeight(30);
        medicineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        medicineTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(medicineTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Your medicine schedule"));
        add(scroll, BorderLayout.CENTER);

        JButton addButton = new JButton("Add medicine");
        JButton takenButton = new JButton("Mark as taken");
        JButton deleteButton = new JButton("Delete");
        JButton historyButton = new JButton("Today's history");
        JButton refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> addMedicine());
        takenButton.addActionListener(e -> markAsTaken());
        deleteButton.addActionListener(e -> deleteMedicine());
        historyButton.addActionListener(e -> showHistory());
        refreshButton.addActionListener(e -> refreshTable());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        actions.add(addButton);
        actions.add(takenButton);
        actions.add(deleteButton);
        actions.add(historyButton);
        actions.add(refreshButton);

        JLabel note = new JLabel(
                "Reminder pop-ups work while MediMate is running. Follow your clinician's prescribed dosage.");
        note.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        note.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(note, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void addMedicine() {
        JTextField nameField = new JTextField();
        JTextField dosageField = new JTextField();
        JTextField timeField = new JTextField("09:00");

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Medicine name:"));
        form.add(nameField);
        form.add(new JLabel("Dosage (example: 1 tablet):"));
        form.add(dosageField);
        form.add(new JLabel("Reminder time (24-hour HH:mm):"));
        form.add(timeField);

        int result = JOptionPane.showConfirmDialog(this, form,
                "Add a medicine", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        String dosage = dosageField.getText().trim();

        if (name.isEmpty() || dosage.isEmpty()) {
            showError("Please enter the medicine name and dosage.");
            return;
        }

        LocalTime reminder;
        try {
            reminder = LocalTime.parse(timeField.getText().trim(), TIME_FORMAT);
        } catch (Exception ex) {
            showError("Enter a valid time, for example 08:30 or 21:00.");
            return;
        }

        medicines.add(new Medicine(currentUser, name, dosage, reminder));
        saveMedicines();
        refreshTable();
    }

    private void markAsTaken() {
        int selected = medicineTable.getSelectedRow();
        if (selected < 0) {
            showError("Select a medicine first.");
            return;
        }

        Medicine medicine = medicines.get(medicineTable.convertRowIndexToModel(selected));
        medicine.lastTakenDate = LocalDate.now().toString();
        medicine.lastTakenAt = LocalTime.now().format(TIME_FORMAT);
        saveMedicines();
        refreshTable();

        JOptionPane.showMessageDialog(this,
                medicine.name + " marked as taken.",
                "MediMate", JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteMedicine() {
        int selected = medicineTable.getSelectedRow();
        if (selected < 0) {
            showError("Select a medicine to delete.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "Delete this medicine reminder?",
                "Confirm deletion", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            medicines.remove(medicineTable.convertRowIndexToModel(selected));
            saveMedicines();
            refreshTable();
        }
    }

    private void showHistory() {
        String today = LocalDate.now().toString();
        StringBuilder history = new StringBuilder("Medicine history for ")
                .append(today).append("\n\n");

        boolean found = false;
        for (Medicine m : medicines) {
            if (today.equals(m.lastTakenDate)) {
                history.append("• ").append(m.name)
                        .append(" - ").append(m.dosage)
                        .append(" - Taken at ").append(m.lastTakenAt)
                        .append("\n");
                found = true;
            }
        }

        if (!found) history.append("No medicines marked as taken today.");
        JTextArea area = new JTextArea(history.toString(), 12, 42);
        area.setEditable(false);
        area.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Daily medicine history", JOptionPane.INFORMATION_MESSAGE);
    }

    private void checkReminders() {
        LocalTime now = LocalTime.now();
        String today = LocalDate.now().toString();
        String currentMinute = now.format(TIME_FORMAT);

        for (Medicine m : medicines) {
            String alertKey = m.name + "|" + m.reminderTime + "|" + today;
            boolean due = m.reminderTime.getHour() == now.getHour()
                    && m.reminderTime.getMinute() == now.getMinute();

            if (due && !today.equals(m.lastTakenDate)
                    && !alertsShown.contains(alertKey)) {
                alertsShown.add(alertKey);
                JOptionPane.showMessageDialog(this,
                        "It is time for your scheduled medicine:\n\n"
                                + m.name + "\nDosage: " + m.dosage
                                + "\nTime: " + currentMinute
                                + "\n\nPlease follow the dosage prescribed by your clinician.",
                        "MediMate reminder", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        String today = LocalDate.now().toString();

        for (Medicine m : medicines) {
            String status = today.equals(m.lastTakenDate)
                    ? "Taken at " + m.lastTakenAt : "Pending";
            tableModel.addRow(new Object[]{
                    m.name, m.dosage, m.reminderTime.format(TIME_FORMAT), status
            });
        }
    }

    private void loadMedicines() {
        File file = new File(MEDICINES_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\t", -1);
                if (p.length != 6) continue;
                try {
                    String owner = decode(p[0]);
                    if (!owner.equals(currentUser)) continue;
                    Medicine m = new Medicine(owner, decode(p[1]), decode(p[2]),
                            LocalTime.parse(p[3], TIME_FORMAT));
                    m.lastTakenDate = decode(p[4]);
                    m.lastTakenAt = decode(p[5]);
                    medicines.add(m);
                } catch (Exception ignored) {
                    // Ignore an invalid data row.
                }
            }
        } catch (IOException ex) {
            showError("Could not read medicine data: " + ex.getMessage());
        }
    }

    private void saveMedicines() {
        // Read all users' records first, so saving one account does not erase another.
        List<String> allLines = new ArrayList<>();
        File file = new File(MEDICINES_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] p = line.split("\\t", -1);
                    if (p.length == 6 && !decodeSafe(p[0]).equals(currentUser)) {
                        allLines.add(line);
                    }
                }
            } catch (IOException ignored) { }
        }

        for (Medicine m : medicines) {
            allLines.add(encode(m.owner) + "\t" + encode(m.name) + "\t"
                    + encode(m.dosage) + "\t" + m.reminderTime.format(TIME_FORMAT)
                    + "\t" + encode(m.lastTakenDate) + "\t" + encode(m.lastTakenAt));
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : allLines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException ex) {
            showError("Could not save medicine data: " + ex.getMessage());
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String decodeSafe(String value) {
        try { return decode(value); }
        catch (Exception ex) { return ""; }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "MediMate", JOptionPane.ERROR_MESSAGE);
    }

    private static class Medicine {
        String owner;
        String name;
        String dosage;
        LocalTime reminderTime;
        String lastTakenDate = "";
        String lastTakenAt = "";

        Medicine(String owner, String name, String dosage, LocalTime reminderTime) {
            this.owner = owner;
            this.name = name;
            this.dosage = dosage;
            this.reminderTime = reminderTime;
        }
    }

    private static class LoginDialog extends JDialog {
        String loggedInUser;

        LoginDialog(Frame parent) {
            super(parent, "MediMate Login", true);
            setLayout(new BorderLayout(10, 10));

            JLabel title = new JLabel("MediMate", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 26));

            JTextField username = new JTextField(18);
            JPasswordField password = new JPasswordField(18);

            JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
            form.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
            form.add(new JLabel("Username"));
            form.add(username);
            form.add(new JLabel("Password"));
            form.add(password);

            JButton login = new JButton("Login");
            JButton register = new JButton("Create account");
            JButton cancel = new JButton("Exit");

            login.addActionListener(e -> {
                String user = username.getText().trim();
                char[] pass = password.getPassword();
                if (authenticate(user, pass)) {
                    loggedInUser = user;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Incorrect username or password.",
                            "Login failed", JOptionPane.ERROR_MESSAGE);
                }
                Arrays.fill(pass, '\0');
            });

            register.addActionListener(e -> {
                String user = username.getText().trim();
                char[] pass = password.getPassword();
                if (user.length() < 3 || pass.length < 4) {
                    JOptionPane.showMessageDialog(this,
                            "Username must have at least 3 characters and password at least 4.",
                            "Invalid details", JOptionPane.WARNING_MESSAGE);
                } else if (registerUser(user, pass)) {
                    JOptionPane.showMessageDialog(this,
                            "Account created. You can now log in.");
                } else {
                    JOptionPane.showMessageDialog(this,
                            "That username already exists.",
                            "Registration failed", JOptionPane.ERROR_MESSAGE);
                }
                Arrays.fill(pass, '\0');
            });

            cancel.addActionListener(e -> dispose());

            JPanel buttons = new JPanel(new FlowLayout());
            buttons.add(login);
            buttons.add(register);
            buttons.add(cancel);

            add(title, BorderLayout.NORTH);
            add(form, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }

        private boolean authenticate(String user, char[] password) {
            if (user.isEmpty()) return false;
            Map<String, String> users = readUsers();
            String saved = users.get(user);
            if (saved == null) return false;

            String[] parts = saved.split(":", 2);
            if (parts.length != 2) return false;
            try {
                byte[] salt = Base64.getDecoder().decode(parts[0]);
                byte[] expected = Base64.getDecoder().decode(parts[1]);
                byte[] actual = passwordHash(password, salt);
                return MessageDigest.isEqual(expected, actual);
            } catch (Exception ex) {
                return false;
            }
        }

        private boolean registerUser(String user, char[] password) {
            Map<String, String> users = readUsers();
            if (users.containsKey(user)) return false;

            try {
                byte[] salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                byte[] hash = passwordHash(password, salt);
                users.put(user, Base64.getEncoder().encodeToString(salt)
                        + ":" + Base64.getEncoder().encodeToString(hash));
                writeUsers(users);
                return true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not create account: " + ex.getMessage());
                return false;
            }
        }

        private Map<String, String> readUsers() {
            Map<String, String> users = new LinkedHashMap<>();
            File file = new File(USERS_FILE);
            if (!file.exists()) return users;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) users.put(decode(line.substring(0, tab)),
                            line.substring(tab + 1));
                }
            } catch (IOException ignored) { }
            return users;
        }

        private void writeUsers(Map<String, String> users) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
                for (Map.Entry<String, String> entry : users.entrySet()) {
                    writer.write(encode(entry.getKey()) + "\t" + entry.getValue());
                    writer.newLine();
                }
            }
        }

        private static byte[] passwordHash(char[] password, byte[] salt)
                throws Exception {
            // PBKDF2 is used instead of saving a readable password.
            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(password, salt, 60000, 256);
            try {
                return javax.crypto.SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}

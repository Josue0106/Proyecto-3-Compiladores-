/*
 * IDE-Triangle v1.0
 * Main.java
 */

package GUI;
import Core.Console.InputRedirector;
import Core.Console.OutputRedirector;
import Core.IDE.IDEDisassembler;
import Core.IDE.IDEInterpreter;
import Core.Visitors.TableVisitor;
// import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.ImageIcon;
import java.awt.Image;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import Triangle.CodeGenerator.LLVM.NativeToolchain;
import Triangle.IDECompiler;
import Core.ExampleFileFilter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import Core.Visitors.TreeVisitor;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.SwingUtilities;
import java.io.InputStreamReader;

/**
 * The Main class. Contains the main form.
 *
 * @author Luis Leopoldo P�rez <luiperpe@ns.isi.ulatina.ac.cr>
 */
public class Main extends javax.swing.JFrame {

    // <editor-fold defaultstate="collapsed" desc=" Methods ">
    
    /**
     * Creates new form Main.
     */
    public Main() {        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());            
        } catch (Exception e) { }
        
        initComponents();
        setSize(640, 480);
        setVisible(true);
        directory = new File(".");
        compiler.setEmitLlvm(false);
        llvmMenuItem.setSelected(false);
        updateLlvmUiState();
    }
    
    /**
     * Checks if the file has been changed - used to enable/disable 
     * the "Save" button.
     */
    private void checkSaveChanges() {
        if (((FileFrame)desktopPane.getSelectedFrame()).hasChanged()) {
            buttonSave.setEnabled(true);
            saveMenuItem.setEnabled(true);
        }
        else {
            buttonSave.setEnabled(false);
            saveMenuItem.setEnabled(false);
        }
    }
    
    /**
     * Checks if there are any open documents. Disables some buttons and
     * menu options if no document is open. 
     */
    private void checkPaneChanges() {
        if (desktopPane.getComponentCount() == 0) {
            saveAsMenuItem.setEnabled(false);
            saveMenuItem.setEnabled(false);
            buttonSave.setEnabled(false);            
            buttonCut.setEnabled(false);
            buttonCopy.setEnabled(false);
            buttonPaste.setEnabled(false);            
            buttonCompile.setEnabled(false);
            buttonRun.setEnabled(false);
            cutMenuItem.setEnabled(false);
            copyMenuItem.setEnabled(false);
            pasteMenuItem.setEnabled(false);            
            compileMenuItem.setEnabled(false);
            runMenuItem.setEnabled(false);           
        } else
            checkSaveChanges();

        updateLlvmUiState();
    }

    private void updateLlvmUiState() {
        if (saveLlvmMenuItem == null) {
            return;
        }
        boolean emitLlvm = compiler.isEmitLlvm();
        boolean hasModule = emitLlvm && compiler.hasLlvmModule();
        boolean hasNative = compiler.hasNativeExecutable();
        saveLlvmMenuItem.setEnabled(hasModule);
        if (saveAsmMenuItem != null) {
            saveAsmMenuItem.setEnabled(hasModule);
        }
        if (saveObjMenuItem != null) {
            saveObjMenuItem.setEnabled(hasModule);
        }
        if (viewLlvmMenuItem != null) {
            viewLlvmMenuItem.setEnabled(hasModule);
        }
        if (compileNativeMenuItem != null) {
            compileNativeMenuItem.setEnabled(hasModule);
        }
        if (runNativeMenuItem != null) {
            runNativeMenuItem.setEnabled(hasNative);
        }
        if (runNativeShellMenuItem != null) {
            runNativeShellMenuItem.setEnabled(hasNative);
        }
    }

    private void setNativeActionsBusy(boolean busy) {
        if (busy) {
            if (compileNativeMenuItem != null) {
                compileNativeMenuItem.setEnabled(false);
            }
            if (runNativeMenuItem != null) {
                runNativeMenuItem.setEnabled(false);
            }
        } else {
            updateLlvmUiState();
        }
    }

    private void setNativeExecutionRunning(boolean running) {
        if (running) {
            compileMenuItem.setEnabled(false);
            buttonCompile.setEnabled(false);
            runMenuItem.setEnabled(false);
            buttonRun.setEnabled(false);
            if (compileNativeMenuItem != null) {
                compileNativeMenuItem.setEnabled(false);
            }
            if (runNativeMenuItem != null) {
                runNativeMenuItem.setEnabled(false);
            }
        } else {
            delegateRun.actionPerformed(null);
            updateLlvmUiState();
        }
    }
    
    /**
     * Creates a new document frame and inserts it into the desktop pane,
     * enabling its buttons and menu options.
     *
     * @param title Title of the frame.
     * @param contents File contents.
     * @return The new file frame.
     */
    private FileFrame addInternalFrame(String title, String contents) {
        FileFrame x = new FileFrame(delegateSaveButton, delegateMouse, delegateInternalFrame, delegateEnter);
        
        x.setTitle(title);
        x.setSize(540, 250);
        x.setSourcePaneText(contents);
        x.setPreviousSize(contents.length());
        x.setPreviousText(contents);
        desktopPane.add(x);        
        x.setVisible(true);
        
        saveAsMenuItem.setEnabled(true);        
        buttonCut.setEnabled(true);
        buttonCopy.setEnabled(true);
        buttonPaste.setEnabled(true);            
        cutMenuItem.setEnabled(true);
        copyMenuItem.setEnabled(true);
        pasteMenuItem.setEnabled(true);
        compileMenuItem.setEnabled(true);
        buttonCompile.setEnabled(true);
       
        checkSaveChanges();        
        return(x);
    }
    
    /**
     * Opens a file chooser dialog, used in the "Open" and "Save" options.
     * @return The JFileChooser dialog object.
     */
    private JFileChooser drawFileChooser() {
        JFileChooser chooser = new JFileChooser();
        ExampleFileFilter filter = new ExampleFileFilter();        
        filter.setDescription("Triangle files");
        filter.addExtension("tri");
        chooser.setFileFilter(filter);
        chooser.setCurrentDirectory(directory);
        
        return(chooser);
    }

    private String replaceExtension(String fileName, String newExtension) {
        if (fileName == null) {
            return newExtension;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            return fileName.substring(0, dot) + newExtension;
        }
        return fileName + newExtension;
    }

    /**
     * Main method, instantiates the Main class.
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Main();
            }
        });
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        toolBarsPanel = new javax.swing.JPanel();
        fileToolBar = new javax.swing.JToolBar();
        buttonNew = new javax.swing.JButton();
        buttonOpen = new javax.swing.JButton();
        buttonSave = new javax.swing.JButton();
        editToolBar = new javax.swing.JToolBar();
        buttonCut = new javax.swing.JButton();
        buttonCopy = new javax.swing.JButton();
        buttonPaste = new javax.swing.JButton();
        triangleToolBar = new javax.swing.JToolBar();
        buttonCompile = new javax.swing.JButton();
        buttonRun = new javax.swing.JButton();
        desktopPane = new javax.swing.JDesktopPane();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newMenuItem = new javax.swing.JMenuItem();
        openMenuItem = new javax.swing.JMenuItem();
        saveMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        separatorExit = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        triangleMenu = new javax.swing.JMenu();
    llvmMenuItem = new JCheckBoxMenuItem();
        saveLlvmMenuItem = new javax.swing.JMenuItem();
    optimizationMenuItem = new javax.swing.JMenuItem();
        compileNativeMenuItem = new javax.swing.JMenuItem();
        runNativeMenuItem = new javax.swing.JMenuItem();
        compileMenuItem = new javax.swing.JMenuItem();
        runMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("IDE-Triangle 1.1");
        setFont(new java.awt.Font("Tahoma", 0, 11));
        setIconImage(new ImageIcon(this.getClass().getResource("Icons/iconMain.gif")).getImage());
        setLocationByPlatform(true);
        setName("mainFrame");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        toolBarsPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        toolBarsPanel.setFocusable(false);
        fileToolBar.setFocusable(false);
        fileToolBar.setName("File");
        fileToolBar.setRequestFocusEnabled(false);
        buttonNew.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileNew.gif")));
        buttonNew.setToolTipText("New...");
        buttonNew.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonNew.setBorderPainted(false);
        buttonNew.setFocusPainted(false);
        buttonNew.setFocusable(false);
        buttonNew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newMenuItemActionPerformed(evt);
            }
        });

        fileToolBar.add(buttonNew);

        buttonOpen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileOpen.gif")));
        buttonOpen.setToolTipText("Open...");
        buttonOpen.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonOpen.setBorderPainted(false);
        buttonOpen.setFocusPainted(false);
        buttonOpen.setFocusable(false);
        buttonOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });

        fileToolBar.add(buttonOpen);

        buttonSave.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileSave.gif")));
        buttonSave.setToolTipText("Save...");
        buttonSave.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonSave.setBorderPainted(false);
        buttonSave.setEnabled(false);
        buttonSave.setFocusPainted(false);
        buttonSave.setFocusable(false);
        buttonSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMenuItemActionPerformed(evt);
            }
        });

        fileToolBar.add(buttonSave);

        toolBarsPanel.add(fileToolBar);

        editToolBar.setFocusable(false);
        editToolBar.setName("Edit");
        editToolBar.setRequestFocusEnabled(false);
        buttonCut.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditCut.gif")));
        buttonCut.setToolTipText("Cut...");
        buttonCut.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonCut.setBorderPainted(false);
        buttonCut.setEnabled(false);
        buttonCut.setFocusPainted(false);
        buttonCut.setFocusable(false);
        buttonCut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cutMenuItemActionPerformed(evt);
            }
        });

        editToolBar.add(buttonCut);

        buttonCopy.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditCopy.gif")));
        buttonCopy.setToolTipText("Copy...");
        buttonCopy.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonCopy.setBorderPainted(false);
        buttonCopy.setEnabled(false);
        buttonCopy.setFocusPainted(false);
        buttonCopy.setFocusable(false);
        buttonCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyMenuItemActionPerformed(evt);
            }
        });

        editToolBar.add(buttonCopy);

        buttonPaste.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditPaste.gif")));
        buttonPaste.setToolTipText("Paste...");
        buttonPaste.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonPaste.setBorderPainted(false);
        buttonPaste.setEnabled(false);
        buttonPaste.setFocusPainted(false);
        buttonPaste.setFocusable(false);
        buttonPaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteMenuItemActionPerformed(evt);
            }
        });

        editToolBar.add(buttonPaste);

        toolBarsPanel.add(editToolBar);

        triangleToolBar.setFocusable(false);
        triangleToolBar.setName("Triangle");
        triangleToolBar.setRequestFocusEnabled(false);
        buttonCompile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconTriangleCompile.gif")));
        buttonCompile.setToolTipText("Compile...");
        buttonCompile.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonCompile.setBorderPainted(false);
        buttonCompile.setEnabled(false);
        buttonCompile.setFocusPainted(false);
        buttonCompile.setFocusable(false);
        buttonCompile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compileMenuItemActionPerformed(evt);
            }
        });

        triangleToolBar.add(buttonCompile);

        buttonRun.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconTriangleRun.gif")));
        buttonRun.setToolTipText("Run...");
        buttonRun.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        buttonRun.setBorderPainted(false);
        buttonRun.setEnabled(false);
        buttonRun.setFocusPainted(false);
        buttonRun.setFocusable(false);
        buttonRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runMenuItemActionPerformed(evt);
            }
        });

        triangleToolBar.add(buttonRun);

        toolBarsPanel.add(triangleToolBar);

        getContentPane().add(toolBarsPanel, java.awt.BorderLayout.NORTH);

        desktopPane.setBackground(new java.awt.Color(0, 103, 201));
        desktopPane.setAutoscrolls(true);
        getContentPane().add(desktopPane, java.awt.BorderLayout.CENTER);

        menuBar.setFont(new java.awt.Font("Verdana", 0, 11));
        fileMenu.setMnemonic('F');
        fileMenu.setText("File");
        fileMenu.setBorderPainted(true);
        newMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        newMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileNew.gif")));
        newMenuItem.setMnemonic('N');
        newMenuItem.setText("New");
        newMenuItem.setRequestFocusEnabled(false);
        newMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(newMenuItem);

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        openMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileOpen.gif")));
        openMenuItem.setMnemonic('O');
        openMenuItem.setText("Open");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(openMenuItem);

        saveMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        saveMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconFileSave.gif")));
        saveMenuItem.setMnemonic('S');
        saveMenuItem.setText("Save");
        saveMenuItem.setEnabled(false);
        saveMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(saveMenuItem);

        saveAsMenuItem.setMnemonic('A');
        saveAsMenuItem.setText("Save As...");
        saveAsMenuItem.setEnabled(false);
        saveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(saveAsMenuItem);

        fileMenu.add(separatorExit);

        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });

        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        editMenu.setMnemonic('E');
        editMenu.setText("Edit");
        editMenu.setBorderPainted(true);
        cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
        cutMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditCut.gif")));
        cutMenuItem.setMnemonic('t');
        cutMenuItem.setText("Cut");
        cutMenuItem.setEnabled(false);
        cutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cutMenuItemActionPerformed(evt);
            }
        });

        editMenu.add(cutMenuItem);

        copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_MASK));
        copyMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditCopy.gif")));
        copyMenuItem.setMnemonic('C');
        copyMenuItem.setText("Copy");
        copyMenuItem.setEnabled(false);
        copyMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyMenuItemActionPerformed(evt);
            }
        });

        editMenu.add(copyMenuItem);

        pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
        pasteMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconEditPaste.gif")));
        pasteMenuItem.setMnemonic('P');
        pasteMenuItem.setText("Paste");
        pasteMenuItem.setToolTipText("");
        pasteMenuItem.setEnabled(false);
        pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteMenuItemActionPerformed(evt);
            }
        });

        editMenu.add(pasteMenuItem);

        menuBar.add(editMenu);

        triangleMenu.setMnemonic('T');
        triangleMenu.setText("Triangle");
        triangleMenu.setBorderPainted(true);
        llvmMenuItem.setMnemonic('L');
        llvmMenuItem.setText("Emit LLVM IR");
        llvmMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                llvmMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(llvmMenuItem);

        saveLlvmMenuItem.setMnemonic('V');
        saveLlvmMenuItem.setText("Save LLVM IR...");
        saveLlvmMenuItem.setEnabled(false);
        saveLlvmMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveLlvmMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(saveLlvmMenuItem);

        optimizationMenuItem.setMnemonic('Z');
        optimizationMenuItem.setText("Optimizations...");
        optimizationMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                optimizationMenuItemActionPerformed(evt);
            }
        });
        triangleMenu.add(optimizationMenuItem);

        saveAsmMenuItem = new javax.swing.JMenuItem();
        saveAsmMenuItem.setMnemonic('A');
        saveAsmMenuItem.setText("Save Native Assembly...");
        saveAsmMenuItem.setEnabled(false);
        saveAsmMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsmMenuItemActionPerformed(evt);
            }
        });
        triangleMenu.add(saveAsmMenuItem);

        saveObjMenuItem = new javax.swing.JMenuItem();
        saveObjMenuItem.setMnemonic('O');
        saveObjMenuItem.setText("Save Native Object...");
        saveObjMenuItem.setEnabled(false);
        saveObjMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveObjMenuItemActionPerformed(evt);
            }
        });
        triangleMenu.add(saveObjMenuItem);

        viewLlvmMenuItem = new javax.swing.JMenuItem();
        viewLlvmMenuItem.setMnemonic('I');
        viewLlvmMenuItem.setText("View LLVM IR");
        viewLlvmMenuItem.setEnabled(false);
        viewLlvmMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewLlvmMenuItemActionPerformed(evt);
            }
        });
        triangleMenu.add(viewLlvmMenuItem);

        compileNativeMenuItem.setMnemonic('N');
        compileNativeMenuItem.setText("Compile Native");
        compileNativeMenuItem.setEnabled(false);
        compileNativeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compileNativeMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(compileNativeMenuItem);

        runNativeMenuItem.setMnemonic('E');
        runNativeMenuItem.setText("Run Native");
        runNativeMenuItem.setEnabled(false);
        runNativeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runNativeMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(runNativeMenuItem);

        runNativeShellMenuItem = new javax.swing.JMenuItem();
        runNativeShellMenuItem.setMnemonic('W');
        runNativeShellMenuItem.setText("Run Native in Shell");
        runNativeShellMenuItem.setEnabled(false);
        runNativeShellMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runNativeShellMenuItemActionPerformed(evt);
            }
        });
        triangleMenu.add(runNativeShellMenuItem);

        compileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
        compileMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconTriangleCompile.gif")));
        compileMenuItem.setMnemonic('C');
        compileMenuItem.setText("Compile");
        compileMenuItem.setEnabled(false);
        compileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compileMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(compileMenuItem);

        runMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
        runMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconTriangleRun.gif")));
        runMenuItem.setMnemonic('R');
        runMenuItem.setText("Run");
        runMenuItem.setEnabled(false);
        runMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runMenuItemActionPerformed(evt);
            }
        });

        triangleMenu.add(runMenuItem);

        menuBar.add(triangleMenu);

        helpMenu.setMnemonic('H');
        helpMenu.setText("Help");
        helpMenu.setBorderPainted(true);
        aboutMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/GUI/Icons/iconHelpAbout.gif")));
        aboutMenuItem.setMnemonic('A');
        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });

        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // <editor-fold defaultstate="collapsed" desc=" Event Handlers Implementation ">
    
    /**
     * Handles the "Run TAM Program" button and menu option.
     */
    private void runMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runMenuItemActionPerformed
        ((FileFrame)desktopPane.getSelectedFrame()).clearConsole();
        ((FileFrame)desktopPane.getSelectedFrame()).selectConsole();
        output.setDelegate(delegateConsole);
        runMenuItem.setEnabled(false);
        buttonRun.setEnabled(false);
        compileMenuItem.setEnabled(false);
        buttonCompile.setEnabled(false);
        interpreter.Run(desktopPane.getSelectedFrame().getTitle().replace(".tri", ".tam"));
    }//GEN-LAST:event_runMenuItemActionPerformed

    /** 
     * Handles the "Close" program option
     */
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        while (desktopPane.getComponentCount() > 0) {
            try { 
                desktopPane.getSelectedFrame().setClosed(true);
            } catch (Exception e) { }
        }
    }//GEN-LAST:event_formWindowClosing

    /** 
     * Handles the "Paste Text" button and menu option.
     */
    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        ((FileFrame)desktopPane.getSelectedFrame()).pasteText(Clip.getClipboardContents());
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    /**
     * Handles the "Cut Text" button and menu option.
     */
    private void cutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cutMenuItemActionPerformed
        Clip.setClipboardContents(((FileFrame)desktopPane.getSelectedFrame()).getSelectedText());
        ((FileFrame)desktopPane.getSelectedFrame()).cutText();
    }//GEN-LAST:event_cutMenuItemActionPerformed

    /**
     * Handles the "Copy Text" button and menu option.
     */
    private void copyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyMenuItemActionPerformed
        Clip.setClipboardContents(((FileFrame)desktopPane.getSelectedFrame()).getSelectedText());
    }//GEN-LAST:event_copyMenuItemActionPerformed

    /** 
     * Handles the "Save As" button and menu option.
     */
    private void saveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsMenuItemActionPerformed
        boolean _previouslySaved = ((FileFrame)desktopPane.getSelectedFrame()).getPreviouslySaved();        
        ((FileFrame)desktopPane.getSelectedFrame()).setPreviouslySaved(false);
        saveMenuItemActionPerformed(evt);
        ((FileFrame)desktopPane.getSelectedFrame()).setPreviouslySaved(_previouslySaved);        
    }//GEN-LAST:event_saveAsMenuItemActionPerformed

    /**
     * Handles the "About" menu option.
     */
    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        new AboutDialog(this, true).setVisible(true);
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    /**
     * Handles the "Open File" button and menu option.
     */
    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
        JFileChooser chooser = drawFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                directory = chooser.getCurrentDirectory();
                BufferedReader br = new BufferedReader(new FileReader(chooser.getSelectedFile()));
                String sr = "";
                while (br.ready())
                    sr += (char)br.read();
                br.close();
                addInternalFrame(chooser.getSelectedFile().getPath(), sr.replace("\r\n", "\n")).setPreviouslySaved(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "An error occurred while trying to open the specified file", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_openMenuItemActionPerformed

    /**
     * Handles the "Compile" button and menu option.
     */
    private void compileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compileMenuItemActionPerformed
        FileFrame currentFrame = (FileFrame)desktopPane.getSelectedFrame();
        if (currentFrame == null) {
            return;
        }
        if ((!currentFrame.getPreviouslySaved()) || currentFrame.hasChanged()) {
            saveMenuItemActionPerformed(null);
        }

        if (currentFrame.getPreviouslySaved()) {
            currentFrame.selectConsole();
            currentFrame.clearConsole();
            currentFrame.clearTAMCode();
            currentFrame.clearLlvmModule();
            currentFrame.clearTree();
            currentFrame.clearTable();
            String sourceName = currentFrame.getTitle();
            String tamPath = replaceExtension(sourceName, ".tam");
            String llvmPath = replaceExtension(sourceName, ".ll");
            new File(tamPath).delete();
            new File(llvmPath).delete();
            compiler.clearNativeArtifacts();
            updateLlvmUiState();

            output.setDelegate(delegateConsole);
            if (compiler.compileProgram(sourceName)) {
                output.setDelegate(delegateTAMCode);
                disassembler.Disassemble(tamPath);
                currentFrame.setTree((DefaultMutableTreeNode)treeVisitor.visitProgram(compiler.getAST(), null));
                currentFrame.setTable(tableVisitor.getTable(compiler.getAST()));
                if (compiler.isEmitLlvm() && compiler.getLlvmModule() != null) {
                    currentFrame.writeToLlvmModule(compiler.getLlvmModule());
                }

                runMenuItem.setEnabled(true);
                buttonRun.setEnabled(true);
                updateLlvmUiState();
            } else {
                currentFrame.highlightError(compiler.getErrorPosition());
                runMenuItem.setEnabled(false);
                buttonRun.setEnabled(false);
                updateLlvmUiState();
            }
        }
    }//GEN-LAST:event_compileMenuItemActionPerformed

    private void compileNativeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compileNativeMenuItemActionPerformed
        if (!compiler.isEmitLlvm()) {
            JOptionPane.showMessageDialog(this,
                    "Enable \"Emit LLVM IR\" before compiling to native.",
                    "Native Compilation",
                    JOptionPane.WARNING_MESSAGE);
            updateLlvmUiState();
            return;
        }
        if (!compiler.hasLlvmModule()) {
            JOptionPane.showMessageDialog(this,
                    "Compile the program with \"Emit LLVM IR\" enabled before generating native code.",
                    "Native Compilation",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        FileFrame currentFrame = (FileFrame) desktopPane.getSelectedFrame();
        if (currentFrame != null) {
            currentFrame.selectConsole();
            currentFrame.clearConsole();
        }

        output.setDelegate(delegateConsole);

        setNativeActionsBusy(true);
        Thread worker = new Thread(() -> {
            NativeToolchain.Result result = null;
            String error = null;
            try {
                result = compiler.compileNativeExecutable();
                if (result != null) {
                    System.out.println("Native toolchain command: " + String.join(" ", result.command()));
                    System.out.println("Native toolchain exit code: " + result.exitCode());
                }
            } catch (IllegalStateException ex) {
                error = ex.getMessage();
            } catch (IOException ex) {
                error = ex.getMessage();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                error = "Native compilation interrupted.";
            }

            final NativeToolchain.Result finalResult = result;
            final String finalError = error;
            SwingUtilities.invokeLater(() -> {
                if (finalError != null) {
                    JOptionPane.showMessageDialog(Main.this,
                            finalError,
                            "Native Compilation",
                            JOptionPane.ERROR_MESSAGE);
                } else if (finalResult == null || !finalResult.isSuccess()) {
                    int exit = (finalResult != null) ? finalResult.exitCode() : -1;
                    JOptionPane.showMessageDialog(Main.this,
                            "Native compilation failed (exit code " + exit + ").",
                            "Native Compilation",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    String executablePath = compiler.getLastNativeExecutablePath();
                    if (executablePath != null) {
                        System.out.println("Native executable written to " + executablePath);
                    }
                }
                setNativeActionsBusy(false);
            });
        }, "triangle-native-compile");
        worker.start();
    }//GEN-LAST:event_compileNativeMenuItemActionPerformed

    private void saveLlvmMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveLlvmMenuItemActionPerformed
        if (!compiler.hasLlvmModule()) {
            JOptionPane.showMessageDialog(this,
                    "No LLVM IR module is available. Compile with \"Emit LLVM IR\" enabled first.",
                    "No LLVM IR",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        JFileChooser chooser = new JFileChooser();
        ExampleFileFilter filter = new ExampleFileFilter();
        filter.setDescription("LLVM IR files");
        filter.addExtension("ll");
        chooser.setFileFilter(filter);
        chooser.setCurrentDirectory(directory);

        String suggestedPath = compiler.getLlvmOutputPath();
        if (suggestedPath != null && !suggestedPath.isEmpty()) {
            chooser.setSelectedFile(new File(suggestedPath));
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                if (!selected.getName().toLowerCase().endsWith(".ll")) {
                    String name = selected.getName() + ".ll";
                    File parent = selected.getParentFile();
                    selected = (parent != null) ? new File(parent, name) : new File(name);
                }

                if (selected.exists()) {
                    int overwrite = JOptionPane.showConfirmDialog(this,
                            selected.getName() + " already exists.\nWould you like to replace it?",
                            "Overwrite?",
                            JOptionPane.YES_NO_OPTION);
                    if (overwrite != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                try {
                    compiler.saveLlvmModuleTo(selected);
                    directory = chooser.getCurrentDirectory();
                } catch (IOException | IllegalStateException ex) {
                    JOptionPane.showMessageDialog(this,
                            "An error occurred while trying to save the LLVM IR file:\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        updateLlvmUiState();
    }//GEN-LAST:event_saveLlvmMenuItemActionPerformed

    private void saveAsmMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (!compiler.isEmitLlvm()) {
            JOptionPane.showMessageDialog(this,
                    "Enable \"Emit LLVM IR\" first.",
                    "Save Native Assembly",
                    JOptionPane.WARNING_MESSAGE);
            updateLlvmUiState();
            return;
        }
        if (!compiler.hasLlvmModule()) {
            JOptionPane.showMessageDialog(this,
                    "Compile the program with \"Emit LLVM IR\" enabled before saving assembly.",
                    "Save Native Assembly",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        JFileChooser chooser = new JFileChooser();
        ExampleFileFilter filter = new ExampleFileFilter();
        filter.setDescription("Assembly files");
        filter.addExtension("s");
        chooser.setFileFilter(filter);
        chooser.setCurrentDirectory(directory);

        String suggestedPath = compiler.getLlvmOutputPath();
        if (suggestedPath != null && !suggestedPath.isEmpty()) {
            // Suggest replacing .ll with .s
            File suggested = new File(suggestedPath);
            String name = suggested.getName();
            int dot = name.lastIndexOf('.');
            String asmName = (dot >= 0 ? name.substring(0, dot) : name) + ".s";
            chooser.setSelectedFile(new File(suggested.getParentFile(), asmName));
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                if (!selected.getName().toLowerCase().endsWith(".s")) {
                    selected = new File(selected.getParentFile(), selected.getName() + ".s");
                }

                if (selected.exists()) {
                    int overwrite = JOptionPane.showConfirmDialog(this,
                            selected.getName() + " already exists.\nWould you like to replace it?",
                            "Overwrite?",
                            JOptionPane.YES_NO_OPTION);
                    if (overwrite != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                setNativeActionsBusy(true);
                output.setDelegate(delegateConsole);
                FileFrame currentFrame = (FileFrame) desktopPane.getSelectedFrame();
                if (currentFrame != null) {
                    currentFrame.selectConsole();
                    currentFrame.clearConsole();
                }

                final File outFile = selected;
                Thread worker = new Thread(() -> {
                    Triangle.CodeGenerator.LLVM.NativeToolchain.Result result = null;
                    String error = null;
                    try {
                        result = compiler.compileNativeAssemblyTo(outFile);
                        if (result != null) {
                            System.out.println("Native toolchain command: " + String.join(" ", result.command()));
                            System.out.println("Native toolchain exit code: " + result.exitCode());
                        }
                    } catch (IllegalStateException ex) {
                        error = ex.getMessage();
                    } catch (IOException ex) {
                        error = ex.getMessage();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        error = "Assembly emission interrupted.";
                    }

                    final Triangle.CodeGenerator.LLVM.NativeToolchain.Result finalResult = result;
                    final String finalError = error;
                    SwingUtilities.invokeLater(() -> {
                        if (finalError != null) {
                            JOptionPane.showMessageDialog(Main.this,
                                    finalError,
                                    "Save Native Assembly",
                                    JOptionPane.ERROR_MESSAGE);
                        } else if (finalResult == null || !finalResult.isSuccess()) {
                            int exit = (finalResult != null) ? finalResult.exitCode() : -1;
                            JOptionPane.showMessageDialog(Main.this,
                                    "Failed to generate assembly (exit code " + exit + ").",
                                    "Save Native Assembly",
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            System.out.println("Assembly written to " + outFile.getAbsolutePath());
                        }
                        setNativeActionsBusy(false);
                    });
                }, "triangle-native-asm");
                worker.start();
            }
        }

        updateLlvmUiState();
    }

    private void saveObjMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (!compiler.isEmitLlvm()) {
            JOptionPane.showMessageDialog(this,
                    "Enable \"Emit LLVM IR\" first.",
                    "Save Native Object",
                    JOptionPane.WARNING_MESSAGE);
            updateLlvmUiState();
            return;
        }
        if (!compiler.hasLlvmModule()) {
            JOptionPane.showMessageDialog(this,
                    "Compile the program with \"Emit LLVM IR\" enabled before saving object file.",
                    "Save Native Object",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        JFileChooser chooser = new JFileChooser();
        ExampleFileFilter filter = new ExampleFileFilter();
        filter.setDescription("Object files");
        filter.addExtension("o");
        filter.addExtension("obj");
        chooser.setFileFilter(filter);
        chooser.setCurrentDirectory(directory);

        String suggestedPath = compiler.getLlvmOutputPath();
        if (suggestedPath != null && !suggestedPath.isEmpty()) {
            File suggested = new File(suggestedPath);
            String name = suggested.getName();
            int dot = name.lastIndexOf('.');
            String base = (dot >= 0 ? name.substring(0, dot) : name);
            String objExt = isWindows() ? ".obj" : ".o";
            chooser.setSelectedFile(new File(suggested.getParentFile(), base + objExt));
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                String lower = selected.getName().toLowerCase();
                if (!(lower.endsWith(".o") || lower.endsWith(".obj"))) {
                    selected = new File(selected.getParentFile(), selected.getName() + (isWindows() ? ".obj" : ".o"));
                }

                if (selected.exists()) {
                    int overwrite = JOptionPane.showConfirmDialog(this,
                            selected.getName() + " already exists.\nWould you like to replace it?",
                            "Overwrite?",
                            JOptionPane.YES_NO_OPTION);
                    if (overwrite != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                setNativeActionsBusy(true);
                output.setDelegate(delegateConsole);
                FileFrame currentFrame = (FileFrame) desktopPane.getSelectedFrame();
                if (currentFrame != null) {
                    currentFrame.selectConsole();
                    currentFrame.clearConsole();
                }

                final File outFile = selected;
                Thread worker = new Thread(() -> {
                    Triangle.CodeGenerator.LLVM.NativeToolchain.Result result = null;
                    String error = null;
                    try {
                        result = compiler.compileNativeObjectTo(outFile);
                        if (result != null) {
                            System.out.println("Native toolchain command: " + String.join(" ", result.command()));
                            System.out.println("Native toolchain exit code: " + result.exitCode());
                        }
                    } catch (IllegalStateException ex) {
                        error = ex.getMessage();
                    } catch (IOException ex) {
                        error = ex.getMessage();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        error = "Object emission interrupted.";
                    }

                    final Triangle.CodeGenerator.LLVM.NativeToolchain.Result finalResult = result;
                    final String finalError = error;
                    SwingUtilities.invokeLater(() -> {
                        if (finalError != null) {
                            JOptionPane.showMessageDialog(Main.this,
                                    finalError,
                                    "Save Native Object",
                                    JOptionPane.ERROR_MESSAGE);
                        } else if (finalResult == null || !finalResult.isSuccess()) {
                            int exit = (finalResult != null) ? finalResult.exitCode() : -1;
                            JOptionPane.showMessageDialog(Main.this,
                                    "Failed to generate object file (exit code " + exit + ").",
                                    "Save Native Object",
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            System.out.println("Object file written to " + outFile.getAbsolutePath());
                        }
                        setNativeActionsBusy(false);
                    });
                }, "triangle-native-obj");
                worker.start();
            }
        }

        updateLlvmUiState();
    }

    private void llvmMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_llvmMenuItemActionPerformed
        compiler.setEmitLlvm(llvmMenuItem.isSelected());
        if (!llvmMenuItem.isSelected()) {
            compiler.clearNativeArtifacts();
        }
        updateLlvmUiState();
    }//GEN-LAST:event_llvmMenuItemActionPerformed

    private void runNativeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runNativeMenuItemActionPerformed
        if (!compiler.hasNativeExecutable()) {
            JOptionPane.showMessageDialog(this,
                    "No native executable available. Compile to native first.",
                    "Native Execution",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        File executable = compiler.getNativeExecutableFile();
        if (executable == null || !executable.isFile()) {
            JOptionPane.showMessageDialog(this,
                    "The native executable could not be found on disk.",
                    "Native Execution",
                    JOptionPane.ERROR_MESSAGE);
            updateLlvmUiState();
            return;
        }

        FileFrame currentFrame = (FileFrame) desktopPane.getSelectedFrame();
        if (currentFrame != null) {
            currentFrame.selectConsole();
            currentFrame.clearConsole();
        }

        output.setDelegate(delegateConsole);

        setNativeExecutionRunning(true);
        Thread worker = new Thread(() -> {
            int exitCode = -1;
            String error = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath());
                File parent = executable.getParentFile();
                if (parent != null) {
                    builder.directory(parent);
                }
                builder.redirectErrorStream(true);
                Process process = builder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                exitCode = process.waitFor();
            } catch (IOException ex) {
                error = ex.getMessage();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                error = "Native execution interrupted.";
            }

            final int finalExitCode = exitCode;
            final String finalError = error;
            SwingUtilities.invokeLater(() -> {
                setNativeExecutionRunning(false);
                if (finalError != null) {
                    JOptionPane.showMessageDialog(Main.this,
                            "Failed to execute native binary:\n" + finalError,
                            "Native Execution",
                            JOptionPane.ERROR_MESSAGE);
                } else if (finalExitCode != 0) {
                    JOptionPane.showMessageDialog(Main.this,
                            "Native program exited with code " + finalExitCode + ".",
                            "Native Execution",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    System.out.println("Native program finished successfully.");
                }
            });
        }, "triangle-native-run");
        worker.start();
    }//GEN-LAST:event_runNativeMenuItemActionPerformed

    private void viewLlvmMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (!compiler.hasLlvmModule()) {
            JOptionPane.showMessageDialog(this,
                    "No LLVM IR module is available. Compile with \"Emit LLVM IR\" enabled first.",
                    "No LLVM IR",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }
        FileFrame currentFrame = (FileFrame) desktopPane.getSelectedFrame();
        if (currentFrame != null) {
            currentFrame.writeToLlvmModule(compiler.getLlvmModule());
            currentFrame.selectLlvmModule();
        }
    }

    private void optimizationMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        String current = compiler.getOptimizationLevelName();
        String[] options = new String[] {"NONE", "O1", "O2", "O3"};
        String chosen = (String) JOptionPane.showInputDialog(
                this,
                "Select optimization level (applies to native toolchain):",
                "Optimization",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                current != null ? current : "NONE");
        if (chosen != null && !chosen.isEmpty()) {
            compiler.setOptimizationLevelByName(chosen);
        }
    }

    private void runNativeShellMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (!compiler.hasNativeExecutable()) {
            JOptionPane.showMessageDialog(this,
                    "No native executable available. Compile to native first.",
                    "Native Execution (Shell)",
                    JOptionPane.INFORMATION_MESSAGE);
            updateLlvmUiState();
            return;
        }

        File executable = compiler.getNativeExecutableFile();
        if (executable == null || !executable.isFile()) {
            JOptionPane.showMessageDialog(this,
                    "The native executable could not be found on disk.",
                    "Native Execution (Shell)",
                    JOptionPane.ERROR_MESSAGE);
            updateLlvmUiState();
            return;
        }

        try {
            if (isWindows()) {
                // Create a temporary batch file to keep the console open after execution for user to see output.
                String exePath = executable.getAbsolutePath();
                File dir = executable.getParentFile();
                File launcher = new File(dir, "run_native_triangle.bat");
                try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(launcher))) {
                    bw.write("@echo off" + System.lineSeparator());
                    bw.write("echo Running Triangle native executable..." + System.lineSeparator());
                    bw.write("\"" + exePath + "\"" + System.lineSeparator());
                    bw.write("echo." + System.lineSeparator());
                    bw.write("echo --- Programa finalizado. Presione una tecla para cerrar ---" + System.lineSeparator());
                    bw.write("pause" + System.lineSeparator());
                } catch (Exception ioex) {
                    // Fallback: just run without persistence.
                }
                new ProcessBuilder("cmd.exe", "/c", "start", "Triangle Program", launcher.getName())
                        .directory(dir)
                        .start();
            } else {
                // Attempt common terminals; silently ignore failures and escalate on total failure.
                boolean launched = false;
                String[] terminals = {"xterm", "gnome-terminal", "konsole"};
                for (String term : terminals) {
                    try {
                        ProcessBuilder tb = new ProcessBuilder(term, executable.getAbsolutePath());
                        tb.directory(executable.getParentFile());
                        tb.start();
                        launched = true;
                        break;
                    } catch (IOException ignore) { }
                }
                if (!launched) {
                    throw new IOException("No suitable terminal found (tried xterm/gnome-terminal/konsole)");
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to launch shell: " + ex.getMessage(),
                    "Native Execution (Shell)",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }

    /**
     * Handles the "Save" button and menu option.
     */
    private void saveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuItemActionPerformed
        String fileName = ((FileFrame)desktopPane.getSelectedFrame()).getTitle();
        boolean overwrite = true;
        
        if (!(((FileFrame)desktopPane.getSelectedFrame()).getPreviouslySaved())) {
            JFileChooser chooser = drawFileChooser();
            
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                if (chooser.getSelectedFile().exists()) {
                    overwrite = (JOptionPane.showConfirmDialog(this, chooser.getSelectedFile().getName() + " already exists.\nWould you like to replace it?", "Overwrite?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
                }
                
                directory = chooser.getCurrentDirectory();
                
                fileName = chooser.getSelectedFile().getPath();
                if (!(fileName.contains(".tri")))
                    fileName += ".tri";
            } else
                overwrite = false;
        }
 
        if (overwrite) {
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
                bw.write(((FileFrame)desktopPane.getSelectedFrame()).getSourcePaneText().replace("\n", "\r\n"));
                bw.close();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "An error occurred while trying to save the specified file", "Error", JOptionPane.ERROR_MESSAGE);
            }

            ((FileFrame)desktopPane.getSelectedFrame()).setPreviouslySaved(true);
            ((FileFrame)desktopPane.getSelectedFrame()).setTitle(fileName);            
            ((FileFrame)desktopPane.getSelectedFrame()).setPreviouslyModified(false);
            ((FileFrame)desktopPane.getSelectedFrame()).setPreviousSize(((FileFrame)desktopPane.getSelectedFrame()).getSourcePaneText().length());
            ((FileFrame)desktopPane.getSelectedFrame()).setPreviousText(((FileFrame)desktopPane.getSelectedFrame()).getSourcePaneText());
                
            saveMenuItem.setEnabled(false);
            buttonSave.setEnabled(false);
        }
    }//GEN-LAST:event_saveMenuItemActionPerformed

    /**
     * Handles the "New File" button and menu option.
     */
    private void newMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newMenuItemActionPerformed
        addInternalFrame("Untitled-" + String.valueOf(untitledCount), "");      
        untitledCount++;
    }//GEN-LAST:event_newMenuItemActionPerformed

    /**
     * Handles the "Exit" menu option.
     */
    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        formWindowClosing(null);
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    // </editor-fold>    
           
    // <editor-fold defaultstate="collapsed" desc=" Delegates and Listeners ">    
    /**
     * Runs every time a key is pressed in the text editor frame, thus
     * determining if the file contents have changed, activating the
     * "Save" button and menu option.
     */   
    KeyAdapter delegateSaveButton = new KeyAdapter() {
        public void keyReleased(java.awt.event.KeyEvent evt) {
             checkSaveChanges();
             ((FileFrame)desktopPane.getSelectedFrame()).UpdateRowColNumbers();
        }
    };
    
    MouseListener delegateMouse = new MouseListener() {
        public void mouseClicked(java.awt.event.MouseEvent evt) {
            ((FileFrame)desktopPane.getSelectedFrame()).UpdateRowColNumbers();
        }
        
        public void mouseExited(java.awt.event.MouseEvent evt) {
        }

        public void mouseEntered(java.awt.event.MouseEvent evt) {
        }        
        
        public void mouseReleased(java.awt.event.MouseEvent evt) {
        }       

        public void mousePressed(java.awt.event.MouseEvent evt) {
        }        
    };
    
    /**
     * Several events for the MDI text editor frames. 
     */
    InternalFrameListener delegateInternalFrame = new InternalFrameListener() {        
        
        /**
         * Every time a frame is focused/activated, some buttons can be
         * enabled (e.g. "Save", "Cut", "Copy", "Paste").
         */
        public void internalFrameActivated(InternalFrameEvent evt) { 
            checkPaneChanges();
            ((FileFrame)desktopPane.getSelectedFrame()).UpdateRowColNumbers();
        }
        
        /**
         * Every time a frame is closed, some buttons can be
         * disabled (e.g. "Save", "Cut", "Copy", "Paste").
         */        
        public void internalFrameClosed(InternalFrameEvent evt) {
            checkPaneChanges();
        }
            
        /**
         * Before closing a frame, checks if the file has not been
         * saved yet.
         */
        public void internalFrameClosing(InternalFrameEvent evt) {
            if (((FileFrame)desktopPane.getSelectedFrame()).hasChanged()) { 
                if (JOptionPane.showConfirmDialog(null, "Do you want to save the changes to " + desktopPane.getSelectedFrame().getTitle() + "?", "Save", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)    
                    saveMenuItemActionPerformed(null);    
            }
        }
        
        // Required by interface - not implemented
        public void internalFrameDeactivated(InternalFrameEvent evt) { }
        public void internalFrameDeiconified(InternalFrameEvent evt) { }
        public void internalFrameIconified(InternalFrameEvent evt) { }
        public void internalFrameOpened(InternalFrameEvent evt) { }
    };
    
    
    /**
     * Used to redirect the console output - writes in the "Console" panel.     
     */
    ActionListener delegateConsole = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            while (output.peekQueue())
                ((FileFrame)desktopPane.getSelectedFrame()).writeToConsole(output.readQueue());
        }
    };
    
    /**
     * Used to redirect the console output - writes in the "TAM Code" pane.
     */
    ActionListener delegateTAMCode = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            while (output.peekQueue())
                ((FileFrame)desktopPane.getSelectedFrame()).writeToTAMCode(output.readQueue());
        }        
    };
    
    /**
     * Used to redirect the console input - enables the "Console Input" text box.
     */
    ActionListener delegateInput = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            ((FileFrame)desktopPane.getSelectedFrame()).setInputEnabled(true);
        }
    };
    
    /**
     * Used to redirect the console input - writes the user input in the console.
     */
    ActionListener delegateEnter = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            ((FileFrame)desktopPane.getSelectedFrame()).setInputEnabled(false);
            input.addInput(((FileFrame)desktopPane.getSelectedFrame()).getInputFieldText() + "\n");
            ((FileFrame)desktopPane.getSelectedFrame()).clearInputField();
        }
    };
    
    /**
     * Used to control running programs - only one TAM program can be run at once
     */
    ActionListener delegateRun = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            runMenuItem.setEnabled(true);
            buttonRun.setEnabled(true);
            compileMenuItem.setEnabled(true);
            buttonCompile.setEnabled(true);
        }
    };
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" GUI Variables ">
    // Variables declaration - do not modify//GEN-BEGIN:variables
    javax.swing.JMenuItem aboutMenuItem;
    javax.swing.JButton buttonCompile;
    javax.swing.JButton buttonCopy;
    javax.swing.JButton buttonCut;
    javax.swing.JButton buttonNew;
    javax.swing.JButton buttonOpen;
    javax.swing.JButton buttonPaste;
    javax.swing.JButton buttonRun;
    javax.swing.JButton buttonSave;
    javax.swing.JMenuItem compileMenuItem;
    javax.swing.JMenuItem copyMenuItem;
    javax.swing.JMenuItem cutMenuItem;
    javax.swing.JDesktopPane desktopPane;
    javax.swing.JMenu editMenu;
    javax.swing.JToolBar editToolBar;
    javax.swing.JMenuItem exitMenuItem;
    javax.swing.JMenu fileMenu;
    javax.swing.JToolBar fileToolBar;
    javax.swing.JMenu helpMenu;
    javax.swing.JMenuBar menuBar;
    javax.swing.JMenuItem newMenuItem;
    javax.swing.JMenuItem openMenuItem;
    javax.swing.JMenuItem pasteMenuItem;
    javax.swing.JMenuItem runMenuItem;
    javax.swing.JMenuItem runNativeMenuItem;
    javax.swing.JMenuItem runNativeShellMenuItem;
    javax.swing.JMenuItem compileNativeMenuItem;
    javax.swing.JMenuItem saveLlvmMenuItem;
    javax.swing.JMenuItem optimizationMenuItem;
    javax.swing.JMenuItem saveAsmMenuItem;
    javax.swing.JMenuItem saveObjMenuItem;
    javax.swing.JMenuItem viewLlvmMenuItem;
    javax.swing.JMenuItem saveAsMenuItem;
    javax.swing.JMenuItem saveMenuItem;
    javax.swing.JSeparator separatorExit;
    javax.swing.JPanel toolBarsPanel;
    javax.swing.JMenu triangleMenu;
    javax.swing.JToolBar triangleToolBar;
    // End of variables declaration//GEN-END:variables
    private JCheckBoxMenuItem llvmMenuItem;
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Non-GUI Variables ">
    // [ Non-GUI variables declaration ]
    int untitledCount = 1;                                                  // Counts "Untitled" document names (e.g. "Untitled-1")
    clipBoard Clip = new clipBoard();                                       // Clipboard Management
    IDECompiler compiler = new IDECompiler();                               // Compiler - Analyzes/generates TAM programs
    IDEDisassembler disassembler = new IDEDisassembler();                   // Disassembler - Generates TAM Code
    IDEInterpreter interpreter = new IDEInterpreter(delegateRun);           // Interpreter - Runs TAM programs
    OutputRedirector output = new OutputRedirector();                       // Redirects the console output
    InputRedirector input = new InputRedirector(delegateInput);             // Redirects console input
    TreeVisitor treeVisitor = new TreeVisitor();                            // Draws the Abstract Syntax Trees
    TableVisitor tableVisitor = new TableVisitor();                         // Draws the Identifier Table
    File directory;                                                         // The current directory.
    // [ End of Non-GUI variables declaration ]
    // </editor-fold>    
    
    // <editor-fold defaultstate="collapsed" desc=" Internal Class - ClipboardOwner ">
    /**
     * ClipboardOwner Class
     * Internal clipboard management class, uses the default system clipboard.
     */
    private class clipBoard implements ClipboardOwner {
        
        /**
         * Required by interface - not implemented
         */
        public void lostOwnership(Clipboard aClipboard, Transferable aContents) { }
        
        /**
         * Sets the clipboard contents
         */
        public void setClipboardContents(String _contents) {
            StringSelection stringSelection = new StringSelection(_contents);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, this);
        }
        
        /**
         * Returns the clipboard contents
         */
        public String getClipboardContents() {
            String ret = "";
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    DataFlavor flavorSet[] = contents.getTransferDataFlavors();
                    boolean canString = false;
                    for (int i=0;i<flavorSet.length;i++)
                        if (flavorSet[i] == DataFlavor.stringFlavor)
                            canString = true;
                    
                    ret = (String)contents.getTransferData(DataFlavor.stringFlavor);
                } catch (Exception e) { }
            }
            return(ret);
        }        
    }
    
    // </editor-fold>
}

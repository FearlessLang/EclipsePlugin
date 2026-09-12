package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;

import controller.Facts;
import controller.Main;
import controller.Names;
import controller.Registry;
import controller.Registry.Entry;
import controller.Session;
import tools.OpenPath;
import userMessages.UserError;
import userMessages.Violation;

/// The manager window: the tiles of the registered projects on the left, the Panel of
/// the selected one on the right. Closing the window only hides it; Quit ends the process.
public final class Window{
  //A divider dragged to the tiles-only edge would otherwise restore to that same edge
  //on every future selection, silently reopening each panel too narrow to see.
  private static final int minPanelWidth= 300;
  public final JFrame frame= new JFrame("Fearless Manager");
  private final Main main;
  private final Registry registry;
  private final JLabel status= new JLabel();
  final Tiles tiles;
  final JSplitPane split;
  private final JPanel body= new JPanel(new BorderLayout());
  private final JMenu running= new JMenu("Running");
  private final JMenu project= new JMenu("Project");
  private final Map<Path,Panel> open= new LinkedHashMap<>();
  private final AtomicBoolean rotating= new AtomicBoolean();
  private final Instant start= Instant.now();
  private final Timer ticker= new Timer(1000,_->tick());
  private Panel shown;
  private int ticks;
  private Window(Main main){
    this.main= main;
    this.registry= main.registry;
    status.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
    tiles= new Tiles(registry,this::isRunning,this::showFolder);
    tiles.setBorder(BorderFactory.createTitledBorder("Registered project folders"));
    split= new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,tiles,new JPanel());
    split.setResizeWeight(0.3);
    split.setDividerLocation(320);
    body.add(tiles,BorderLayout.CENTER);
    frame.setJMenuBar(menuBar());
    frame.add(body,BorderLayout.CENTER);
    frame.add(status,BorderLayout.SOUTH);
    frame.getRootPane().setTransferHandler(dropHandler());
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter(){
      @Override public void windowClosing(WindowEvent e){ frame.setVisible(false); ticker.stop(); }
      @Override public void windowIconified(WindowEvent e){ ticker.stop(); }
      @Override public void windowDeiconified(WindowEvent e){ ticker.start(); }
    });
    frame.setIconImage(Icons.app());
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)){ Taskbar.getTaskbar().setIconImage(Icons.app()); }
    frame.setLocationByPlatform(true);
    var avail= GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    frame.setSize(new Dimension(Math.min(760,avail.width),Math.min(900,avail.height)));//the size a restore (un-maximize) falls back to
    frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    tick();
  }
  public static Window create(Main main){ return onEdt(()->new Window(main)); }
  private static <T> T onEdt(Supplier<T> make){
    if (SwingUtilities.isEventDispatchThread()){ return make.get(); }
    var result= new AtomicReference<T>();
    try{ SwingUtilities.invokeAndWait(()->result.set(make.get())); }
    catch(InterruptedException|InvocationTargetException e){ throw Violation.couldNotStartGui(e); }
    return result.get();
  }
  public void show(){
    SwingUtilities.invokeLater(()->{
      frame.setVisible(true);
      frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
      frame.toFront();
      frame.requestFocus();
      ticker.start();
    });
  }
  public boolean askForget(){
    return onEdt(()->JOptionPane.showConfirmDialog(frame,"""
      Remove Fearless as the program registered to open Fearless projects?

      What your desktop already remembers by hand is left exactly as it is:
      this only removes what Fearless itself registered.""","Fearless",JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
  }
  public void explain(UserError problem){
    SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,problem.getMessage(),"Fearless",JOptionPane.WARNING_MESSAGE));
  }
  public void select(Path folder){ SwingUtilities.invokeLater(()->tiles.select(folder)); }
  public void run(Path folder){ SwingUtilities.invokeLater(()->panel(folder).compileOrRun()); }
  public void terminate(Path folder){ SwingUtilities.invokeLater(()->panel(folder).session.terminate()); }
  public void foldersChanged(){ SwingUtilities.invokeLater(this::foldersChangedHere); }
  public List<String> runningPrograms(){ return open.values().stream().filter(this::live).map(this::describe).toList(); }
  private boolean isRunning(Path folder){ return open.containsKey(folder) && live(open.get(folder)); }
  private boolean live(Panel p){ return p.session.running().isPresent(); }
  private String describe(Panel p){ return registry.of(p.folder()).map(Entry::alias).orElseGet(()->Names.compactName(p.folder()))+" - "+p.session.running().orElseThrow(); }
  private Panel panel(Path folder){ return open.computeIfAbsent(folder,f->new Panel(main,f,this::foldersChangedHere)); }
  private void showFolder(Optional<Path> folder){
    if (folder.isEmpty()){ hidePanel(); return; }
    shown= panel(folder.get());
    shown.reload();
    var where= split.getDividerLocation();
    if (split.getWidth() > 0){ where= Math.min(where,split.getWidth()-minPanelWidth); }
    split.setLeftComponent(tiles);
    split.setRightComponent(shown.panel());
    body.removeAll();
    body.add(split,BorderLayout.CENTER);
    split.setDividerLocation(where);
    fillProjectMenu();
    body.revalidate();
    body.repaint();
  }
  private void hidePanel(){
    shown= null;
    split.setRightComponent(new JPanel());
    body.removeAll();
    body.add(tiles,BorderLayout.CENTER);
    fillProjectMenu();
    body.revalidate();
    body.repaint();
  }
  private JMenuBar menuBar(){
    var res= new JMenuBar();
    var manager= new JMenu("Manager");
    manager.setMnemonic('M');
    manager.add(item("Edit project metadata...",true,this::editMetadata));
    manager.add(item("Show raw project state...",true,this::showRawState));
    manager.add(item("Connect Eclipse...",true,this::connectEclipse));
    manager.addSeparator();
    manager.add(item("Forget association",true,main::forgetAssociation));
    manager.add(item("Quit manager",true,main::quit));
    project.setMnemonic('P');
    running.setMnemonic('R');
    res.add(manager);
    res.add(project);
    res.add(running);
    fillProjectMenu();
    fillRunningMenu();
    return res;
  }
  private static JMenuItem item(String text, boolean enabled, Runnable action){
    var res= new JMenuItem(text);
    res.setEnabled(enabled);
    res.addActionListener(_->action.run());
    return res;
  }
  private void fillProjectMenu(){
    project.removeAll();
    project.add(item("Add folder...",true,this::addFolder));
    project.addSeparator();
    var on= shown;
    project.add(item("Clear cache",on != null,()->on.clearCache()));
    project.add(item("Browse files",on != null,()->on.browse()));
    project.add(item("View documentation",on != null,()->on.openDocs()));
    project.add(item("View base documentation",true,()->OpenPath.open(Session.stdLib("baseCache").resolve("base.html"))));
    project.add(item("Error report",on != null && on.hasProblem(),()->on.report()));
    project.addSeparator();
    project.add(item("Forget project",on != null,()->on.forget()));
  }
  private void fillRunningMenu(){
    running.removeAll();
    var live= open.values().stream().filter(this::live).toList();
    if (live.isEmpty()){ running.add(item("<nothing running>",false,()->{})); return; }
    live.forEach(p->running.add(item(describe(p),true,()->tiles.select(p.folder()))));
  }
  private void addFolder(){
    var chooser= new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setFileFilter(new FileFilter(){
      @Override public boolean accept(File f){ return f.isDirectory() || f.getName().endsWith(Names.ext); }
      @Override public String getDescription(){ return "Fearless project folder or *.fearless file"; }
    });
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setDialogTitle("Add a Fearless project folder");
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION){ return; }
    register(List.of(chooser.getSelectedFile().toPath()));
  }
  void register(List<Path> folders){ main.worker.execute(()->folders.forEach(p->main.register(p.toString(),this::explain))); }
  private TransferHandler dropHandler(){
    return new TransferHandler(){
      @Override public boolean canImport(TransferSupport support){
        if (!Drop.hasFiles(support.getTransferable())){ return false; }
        support.setDropAction(COPY);
        return true;
      }
      @Override public boolean importData(TransferSupport support){
        var paths= Drop.paths(support.getTransferable());
        if (paths.isEmpty()){ return false; }
        register(paths);
        return true;
      }
    };
  }
  private void connectEclipse(){
    var chooser= new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setDialogTitle("Select the Eclipse executable");
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION){ return; }
    var chosen= chooser.getSelectedFile().toPath();
    main.worker.execute(()->{
      String done;
      try{ done= main.eclipse.connect(chosen,main.msgDir()); }
      catch(UserError e){ explain(e); return; }
      SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,done,"Fearless",JOptionPane.INFORMATION_MESSAGE));
    });
  }
  private void editMetadata(){
    var area= new JTextArea(registry.text(),30,100);
    area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    var dialog= new JDialog(frame,"Edit project metadata",true);
    var commit= new JButton("Commit");
    var close= new JButton("Close");
    commit.addActionListener(_->main.worker.execute(()->tryCommit(area.getText(),dialog)));
    close.addActionListener(_->dialog.dispose());
    var buttons= new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(commit);
    buttons.add(close);
    dialog.add(new JScrollPane(area),BorderLayout.CENTER);
    dialog.add(buttons,BorderLayout.SOUTH);
    dialog.pack();
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }
  private void tryCommit(String text, JDialog dialog){
    try{ registry.commit(text); }
    catch(UserError e){ explain(e); return; }
    SwingUtilities.invokeLater(()->{
      dialog.dispose();
      open.values().forEach(Panel::reload);
      foldersChangedHere();
    });
  }
  private void showRawState(){
    var text= new JTextArea(rawState(),30,100);
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    JOptionPane.showMessageDialog(frame,new JScrollPane(text),"Raw project state (everything the manager tracks that is not in the metadata file)",JOptionPane.PLAIN_MESSAGE);
  }
  private String rawState(){
    var sb= new StringBuilder();
    for (var e: registry.all()){
      var facts= Facts.of(e.path(),e.kind());
      sb.append(e.alias()).append("  (").append(e.path()).append(")\n");
      sb.append("  files: ").append(facts.files()).append(", bytes: ").append(facts.bytes()).append('\n');
      sb.append("  last modified: ").append(facts.modified()).append('\n');
      sb.append("  package-data stamp: ").append(facts.jsonStamp()).append(", cache stamp: ").append(facts.cacheStamp()).append('\n');
      sb.append("  cache up to date: ").append(facts.cacheUpToDate()).append('\n');
      sb.append("  structurally valid: ").append(facts.valid());
      facts.problem().ifPresent(p->sb.append(" (").append(p.lines().findFirst().orElse(p)).append(')'));
      sb.append('\n');
      sb.append("  marker file: ").append(Names.markerProblem(e.path(),e.alias()).map(p->p.lines().findFirst().orElse(p)).orElse("ok")).append('\n');
      sb.append("  last compiled: ").append(e.compiled()).append(", last run: ").append(e.run()).append('\n');
      var p= open.get(e.path());
      if (p != null){
        sb.append("  session busy: ").append(p.session.busy()).append('\n');
        sb.append("  session running: ").append(p.session.running().orElse("<no>")).append('\n');
        sb.append("  known mains from last compile: ").append(p.session.mains().orElse(List.of())).append('\n');
      }
      sb.append('\n');
    }
    return sb.isEmpty() ? "<nothing registered>" : sb.toString();
  }
  private void foldersChangedHere(){
    tiles.refresh();
    var known= registry.all();
    main.eclipse.publish(known);
    var live= known.stream().map(Entry::path).toList();
    open.values().stream().filter(p->!live.contains(p.folder())).forEach(p->p.session.terminate());
    open.keySet().removeIf(f->!live.contains(f));
    fillRunningMenu();
    if (shown != null && !live.contains(shown.folder())){ hidePanel(); return; }
    fillProjectMenu();
  }
  private void tick(){
    var up= Duration.between(start,Instant.now()).toSeconds();
    status.setText("Running for "+clock(up)+(shown == null ? "" : "   -   "+shown.status()));
    ticks+= 1;
    if (ticks%3 != 0){ return; }
    if (shown != null){ shown.recheckFreshness(); }
    var registered= registry.all().stream().map(Entry::path).filter(f->shown == null || !f.equals(shown.folder())).toList();
    if (registered.isEmpty() || !rotating.compareAndSet(false,true)){ return; }
    var folder= registered.get(ticks/3%registered.size());
    main.worker.execute(()->{
      var modified= Facts.modified(folder);
      var upToDate= Facts.cacheUpToDate(folder,modified);
      SwingUtilities.invokeLater(()->{ rotating.set(false); tiles.updateFreshness(folder,modified,upToDate); });
    });
  }
  static String clock(long seconds){ return "%02d:%02d:%02d".formatted(seconds/3600,(seconds/60)%60,seconds%60); }
}
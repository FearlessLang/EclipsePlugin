package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import controller.Eclipse;
import realSourceOracle.AutoloadHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import controller.Facts;
import controller.Main;
import controller.Names;
import controller.Registry;
import controller.Registry.Entry;
import controller.Registry.Kind;
import controller.Session;
import core.TName;
import fileSupport.LogFiles;
import tools.Fs;
import tools.OpenPath;
import utils.Join;
import utils.Push;

/// The right half of the window: everything about one registered project, and the
/// buttons that act on it. One Panel per opened project, kept while the project stays registered.
public final class Panel{
  private static final DateTimeFormatter when= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final int iconSize= 32;
  private final Main main;
  private final Registry registry;
  private final Path folder;
  private final Runnable onChange;
  final Session session;
  private final Path console;
  private final AtomicBoolean checking= new AtomicBoolean();
  private final JPanel root= new JPanel(new BorderLayout(8,8));
  private final JTextArea output= named(new JTextArea(10,60),"output");
  private final JScrollPane outputScroll= new JScrollPane(output);
  private final JButton clearOutput= small("Clear output",this::clearAll);
  private final JLayeredPane outputLayer= new JLayeredPane();
  private final JTextArea details= named(new JTextArea(9,40),"details");
  private final JPanel kinds= new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
  private final JPanel mainsBox= named(new JPanel(),"mains");
  private final JScrollPane mainsScroll= new JScrollPane(mainsBox);
  private final JPanel mainsPanel= new JPanel(new BorderLayout());
  private final JPanel linksBox= new JPanel();
  private final Collapsible links= new Collapsible("Links",new JScrollPane(linksBox),false);
  private final Collapsible information= new Collapsible("Information",new JScrollPane(details),true);
  private final JButton openDocs= small("Open docs",this::openDocs);
  private final JButton action= named(new JButton("Compile"),"action");
  private final JLabel name= new JLabel();
  private final JList<LogFiles.Entry> logList= new JList<>();
  private final JScrollPane logScroll= new JScrollPane(logList);
  private final JButton viewLog= small("View",this::viewLog);
  private final JButton copyLog= small("Copy",this::copyLog);
  private final JButton deleteLog= small("Delete",this::deleteLog);
  private final Collapsible logs= new Collapsible("Logs",logScroll,false,viewLog,copyLog,deleteLog);
  private Facts facts;
  Panel(Main main, Path folder, Runnable onChange){
    this.main= main;
    this.registry= main.registry;
    this.folder= folder;
    this.onChange= onChange;
    console= main.eclipse.reports(entry().alias()).resolve("console.txt");
    session= new Session(folder,main.eclipse.reports(entry().alias()),main.worker,this::append,this::refreshLater);
    facts= Facts.of(folder,entry().kind());
    output.setEditable(false);
    output.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    details.setEditable(false);
    details.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    mainsBox.setLayout(new BoxLayout(mainsBox,BoxLayout.Y_AXIS));
    linksBox.setLayout(new BoxLayout(linksBox,BoxLayout.Y_AXIS));
    var pick= new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
    pick.add(named(small("All",()->setAll(true)),"all"));
    pick.add(named(small("None",()->setAll(false)),"none"));
    mainsPanel.add(pick,BorderLayout.NORTH);
    mainsPanel.add(mainsScroll,BorderLayout.CENTER);
    mainsPanel.setBorder(BorderFactory.createEtchedBorder());
    logScroll.setPreferredSize(new Dimension(0,140));
    logList.addListSelectionListener(_->updateLogButtons());
    outputScroll.setBorder(BorderFactory.createTitledBorder("Output"));
    action.addActionListener(_->{ if (session.running().isPresent()){ session.terminate(); } else { compileOrRun(Optional.empty()); } });
    outputLayer.setLayout(null);
    outputLayer.add(outputScroll,JLayeredPane.DEFAULT_LAYER);
    outputLayer.add(clearOutput,JLayeredPane.PALETTE_LAYER);
    outputLayer.addComponentListener(new ComponentAdapter(){
      @Override public void componentResized(ComponentEvent e){ layoutOutput(); }
    });
    root.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
    root.setMinimumSize(new Dimension(0,0));//let the split divider shrink this panel past its natural content width
    var top= new JPanel();
    top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));
    top.add(header());
    top.add(kinds);
    top.add(mainsPanel);
    top.add(links);
    top.add(information);
    top.add(logs);
    root.add(top,BorderLayout.NORTH);
    root.add(outputLayer,BorderLayout.CENTER);
    session.refresh();
    refresh();
  }
  public JPanel panel(){ return root; }
  public Path folder(){ return folder; }
  public Facts facts(){ return facts; }
  void reload(){ session.refresh(); refresh(); }
  static <T extends JComponent> T named(T c, String name){ c.setName(name); return c; }
  static JButton small(String text, Runnable action){
    var res= new JButton(text);
    res.setMargin(new Insets(0,6,0,6));
    res.addActionListener(_->action.run());
    return res;
  }
  //Icon, the action button, then the project's short name: its full path is a row
  //inside Information, where a long path can scroll instead of forcing this row wide.
  private JPanel header(){
    var res= new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
    res.add(new JLabel(new Icons.Badge(Icons.folder(folder,iconSize),iconSize,Icons.Mark.none)));
    res.add(action);
    name.setFont(name.getFont().deriveFont(Font.BOLD,18f));
    res.add(name);
    res.add(openDocs);
    return res;
  }
  //Floats Clear output over the top-right of the Output area, inset past the scrollbar.
  private void layoutOutput(){
    outputScroll.setBounds(0,0,outputLayer.getWidth(),outputLayer.getHeight());
    var d= clearOutput.getPreferredSize();
    var x= Math.max(0,outputLayer.getWidth()-d.width-outputScroll.getVerticalScrollBar().getPreferredSize().width-4);
    clearOutput.setBounds(x,4,d.width,d.height);
  }
  private void clearAll(){ output.setText(""); Fs.writeUtf8(console,""); }
  private void append(String text){
    Eclipse.append(console,text);
    SwingUtilities.invokeLater(()->{
      var bar= outputScroll.getVerticalScrollBar();
      var following= bar.getValue()+bar.getVisibleAmount() >= bar.getMaximum()-16;
      output.append(text);
      if (following){ output.setCaretPosition(output.getDocument().getLength()); }
    });
  }
  private void refreshLater(){ SwingUtilities.invokeLater(()->{ refresh(); onChange.run(); }); }
  String status(){
    if (!session.busy()){ return ""; }
    return session.current()+"   "+Window.clock(session.elapsed().toSeconds());
  }
  void recheckFreshness(){
    if (session.busy() || !checking.compareAndSet(false,true)){ return; }
    main.worker.execute(()->{
      var modified= Facts.modified(folder);
      var upToDate= Facts.cacheUpToDate(folder,modified);
      SwingUtilities.invokeLater(()->{
        checking.set(false);
        if (upToDate == facts.cacheUpToDate() && modified == facts.modified()){ return; }
        refresh();
        onChange.run();
      });
    });
  }
  private void refresh(){
    var entry= entry();
    facts= Facts.of(folder,entry.kind());
    Eclipse.state(console.getParent(),session.mainFiles(),session.running());
    name.setText(entry.alias());
    details.setText(String.join("\n",lines(entry)));
    details.setCaretPosition(0);
    fillKinds(entry);
    fillMains(entry);
    fillLinks(entry);
    fillLogs();
    updateButtons(entry);
    root.revalidate();
    root.repaint();
  }
  private Entry entry(){ return registry.of(folder).orElseThrow(); }
  private Optional<String> problem(){
    var entry= entry();
    return facts.problem().or(()->registry.linkProblem(entry)).or(()->Names.markerProblem(folder,entry.alias()));
  }
  boolean hasProblem(){ return problem().isPresent(); }
  private void setAll(boolean on){ changed(()->registry.update(folder,e->e.withMains(on ? session.mains().orElse(List.of()) : List.of()))); }
  private void fillLogs(){
    logList.setListData(LogFiles.list(folder).toArray(LogFiles.Entry[]::new));
    updateLogButtons();
  }
  private void updateLogButtons(){
    var has= logList.getSelectedValue() != null;
    viewLog.setEnabled(has);
    copyLog.setEnabled(has);
    deleteLog.setEnabled(has);
  }
  private void viewLog(){
    var sel= logList.getSelectedValue();
    showText(Fs.readUtf8(sel.path()),sel.path().getFileName().toString(),JOptionPane.PLAIN_MESSAGE);
  }
  private void copyLog(){
    var selection= new StringSelection(Fs.readUtf8(logList.getSelectedValue().path()));
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection,selection);
  }
  private void deleteLog(){
    var sel= logList.getSelectedValue();
    if (JOptionPane.showConfirmDialog(root,"Delete "+sel.path().getFileName()+"?","Fearless",JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION){ return; }
    Fs.rmTree(sel.path());
    fillLogs();
  }
  private void showText(String text, String title, int kind){
    var area= new JTextArea(text,24,90);
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    JOptionPane.showMessageDialog(root,new JScrollPane(area),title,kind);
  }
  //For a code project: what it can run. Unknown until compiled, a single main needs
  //no choice, and several mains are picked one by one or with All and None.
  private void fillMains(Entry entry){
    mainsBox.removeAll();
    mainsPanel.setVisible(entry.kind() == Kind.code);
    var known= session.mains();
    if (known.isEmpty()){ mainsBox.add(new JLabel("<needs compiling>")); return; }
    if (known.get().size() == 1){ mainsBox.add(new JLabel(known.get().getFirst())); return; }
    var chosen= selectedMains();
    for(var main: known.get()){
      var box= named(new JCheckBox(main,chosen.contains(main)),main);
      box.setEnabled(!session.busy());
      box.addActionListener(_->toggleMain(main,box.isSelected()));
      mainsBox.add(box);
    }
    mainsScroll.setPreferredSize(new Dimension(0,Math.min(3,known.get().size())*26+8));
  }
  private void toggleMain(String main, boolean on){
    var current= selectedMains();
    changed(()->registry.update(folder,e->e.withMains(on ? Push.of(current,main) : current.stream().filter(m->!m.equals(main)).toList())));
  }
  private List<String> selectedMains(){
    var known= session.mains();
    if (known.isEmpty()){ return List.of(); }
    if (known.get().size() == 1){ return known.get(); }
    var chosen= entry().mains();
    return known.get().stream().filter(chosen::contains).toList();
  }
  private void fillKinds(Entry entry){
    kinds.removeAll();
    if (entry.kind() == Kind.idle){
      kinds.add(kindButton("Become data",Kind.dataReadOnly));
      kinds.add(kindButton("Become editable data",Kind.dataReadWrite));
      kinds.add(kindButton("Become code",Kind.code));
      return;
    }
    kinds.add(kindButton("Back to idle",Kind.idle));
  }
  private JButton kindButton(String text, Kind target){
    var res= small(text,()->changed(()->registry.update(folder,e->e.withKind(target))));
    res.setEnabled(!session.busy());
    return res;
  }
  private void fillLinks(Entry entry){
    var iAmCode= entry.kind() == Kind.code;
    linksBox.removeAll();
    links.setVisible(iAmCode || entry.kind().isData());
    if (!links.isVisible()){ return; }
    linksBox.add(new JLabel(iAmCode ? "Data projects this code project reads or edits:" : "Code projects that may read or edit this:"));
    registry.all().stream()
      .filter(o->!o.path().equals(folder))
      .filter(o->iAmCode ? o.kind().isData() : o.kind() == Kind.code)
      .sorted(Comparator.comparing(Entry::alias))
      .forEach(o->linksBox.add(linkRow(entry,o,iAmCode)));
  }
  private JPanel linkRow(Entry me, Entry other, boolean iAmCode){
    var codeEntry= iAmCode ? me : other;
    var dataEntry= iAmCode ? other : me;
    var storedReads= codeEntry.reads().getOrDefault(dataEntry.alias(),List.of());
    var storedEdits= codeEntry.edits().getOrDefault(dataEntry.alias(),List.of());
    var readOn= codeEntry.reads().containsKey(dataEntry.alias());
    var writeOn= codeEntry.edits().containsKey(dataEntry.alias());
    var initial= !storedReads.isEmpty() ? storedReads : !storedEdits.isEmpty() ? storedEdits : List.of(AutoloadHandler.capFirst(dataEntry.alias()));
    var read= new JCheckBox("read",readOn);
    var write= new JCheckBox("write",writeOn);
    var field= new JTextField(String.join(" ",initial),14);
    var canWrite= dataEntry.kind() == Kind.dataReadWrite;
    write.setEnabled(canWrite && readOn);
    field.setEnabled(readOn);
    Runnable apply= ()->applyLink(codeEntry.path(),dataEntry.alias(),field.getText(),read.isSelected(),write.isSelected());
    read.addActionListener(_->apply.run());
    write.addActionListener(_->apply.run());
    field.addFocusListener(new FocusAdapter(){
      @Override public void focusLost(FocusEvent e){ if (read.isSelected()){ apply.run(); } }
    });
    var row= new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
    row.add(read);
    if (canWrite || writeOn){ row.add(write); }
    row.add(new JLabel(other.alias()));
    row.add(field);
    return row;
  }
  private void applyLink(Path codeFolder, String dataAlias, String text, boolean read, boolean write){
    var names= text.isBlank() ? List.<String>of() : List.of(text.strip().split("\\s+"));
    var bad= names.stream().filter(n->!TName.isTypeName(n)).findFirst();
    if (bad.isPresent()){ explainBadAlias("\""+bad.get()+"\" is not a Fearless type name: after any leading underscores, a type name starts with an uppercase letter."); return; }
    if (names.stream().distinct().count() != names.size()){ explainBadAlias("\""+text.strip()+"\" repeats a name: each type name is listed once."); return; }
    var reads= withKey(registry.of(codeFolder).orElseThrow().reads(),dataAlias,read,names);
    var edits= withKey(registry.of(codeFolder).orElseThrow().edits(),dataAlias,read && write,names);
    changed(()->registry.update(codeFolder,e->e.withLinks(reads,edits)));
  }
  private void explainBadAlias(String problem){
    JOptionPane.showMessageDialog(root,problem+"\nSeparate the type names this project uses for that data with spaces, like \"Data1 Data2\".","Fearless",JOptionPane.WARNING_MESSAGE);
    refresh();
  }
  private static Map<String,List<String>> withKey(Map<String,List<String>> map, String key, boolean present, List<String> names){
    var out= new LinkedHashMap<>(map);
    if (present){ out.put(key,names); } else { out.remove(key); }
    return out;
  }
  private void updateButtons(Entry entry){
    var busy= session.busy();
    if (session.running().isPresent()){ action.setText("Terminate"); action.setEnabled(true); openDocs.setEnabled(session.mains().isPresent()); return; }
    if (entry.kind() != Kind.code){
      action.setText("Check");
      action.setEnabled(!busy);
      openDocs.setEnabled(false);
      return;
    }
    var needsCompile= !facts.cacheUpToDate();
    var multi= session.mains().filter(m->m.size() > 1).isPresent();
    action.setText(needsCompile ? "Compile" : multi ? "Run selected" : "Run");
    action.setEnabled(!busy && (needsCompile || !selectedMains().isEmpty()));
    openDocs.setEnabled(session.mains().isPresent());
  }
  void compileOrRun(Optional<String> main){
    information.setOpen(false);
    links.setOpen(false);
    var entry= entry();
    if (entry.kind() != Kind.code){ check(); return; }
    if (facts.cacheUpToDate()){ changed(()->registry.ran(folder,System.currentTimeMillis())); session.run(main.map(List::of).orElseGet(entry::mains)); return; }
    var link= registry.linkProblem(entry);
    if (link.isPresent()){ append(link.get()+"\n"); return; }
    changed(()->registry.compiled(folder,System.currentTimeMillis()));
    session.compile();
  }
  private void check(){
    main.worker.execute(()->{
      var entry= entry();
      var problem= Facts.of(folder,entry.kind()).problem().or(()->registry.linkProblem(entry)).or(()->Names.markerProblem(folder,entry.alias()));
      append(problem.map(p->p+"\n").orElse("--- ok: no problem found ---\n"));
      SwingUtilities.invokeLater(this::refresh);
    });
  }
  void clearCache(){
    Fs.rmTree(folder.resolve(Facts.outDir));
    session.refresh();
    changed(()->{});
  }
  private void changed(Runnable action){
    action.run();
    refresh();
    onChange.run();
  }
  void forget(){
    registry.remove(folder);
    onChange.run();
  }
  void openDocs(){
    var genJava= folder.resolve(Facts.outDir).resolve("gen_java");
    if (!Files.isDirectory(genJava)){ return; }
    Fs.walk(genJava,s->s.filter(p->p.toString().endsWith(".html")).toList()).forEach(OpenPath::open);
  }
  void browse(){
    var chooser= new JFileChooser(folder.toFile());
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setDialogTitle("Files of "+Names.compactName(folder));
    chooser.showOpenDialog(root);
  }
  void report(){ showText(problem().orElseThrow(),"Why this project is invalid",JOptionPane.ERROR_MESSAGE); }
  private List<String> lines(Entry entry){
    var linkProblem= registry.linkProblem(entry);
    var out= new ArrayList<>(List.of(
      row("Folder",folder.toString()),
      row("Alias",entry.alias()),
      row("Marker file",Names.markerProblem(folder,entry.alias()).isEmpty() ? "ok" : "broken - see Error report"),
      row("Kind",entry.kind().text),
      row("Files",facts.files()+""),
      row("Total size",bytes(facts.bytes())),
      row("Last modified",stamp(facts.modified()))));
    if (entry.kind() == Kind.code){
      out.add(row("Package data",stamp(facts.jsonStamp())));
      out.add(row("Compiled cache",facts.cacheUpToDate() ? "up to date" : "needs compiling"));
      out.add(row("Last compile",stamp(entry.compiled())));
      out.add(row("Last run",stamp(entry.run())));
      out.add(row("Packages",Join.of(facts.pkgs(),""," ","","<none>")));
      out.add(row("Mains selected",Join.of(entry.mains(),""," ","","<none>")));
      out.add(row("Reads",Join.of(entry.reads().keySet().stream(),""," ","","<none>")));
      out.add(row("Edits",Join.of(entry.edits().keySet().stream(),""," ","","<none>")));
      out.add(row("Links",linkProblem.isEmpty() ? "ok" : "broken - see Error report"));
    }
    out.add(row("File names",facts.valid() ? "valid" : "broken"));
    return out;
  }
  private static String row(String name, String value){ return "%-16s%s".formatted(name,value); }
  private static String stamp(long millis){ return millis < 0 ? "never" : when.format(Instant.ofEpochMilli(millis)); }
  private static String bytes(long size){
    if (size < 1024){ return size+" bytes"; }
    if (size < 1024*1024){ return "%.1f kB".formatted(size/1024.0); }
    return "%.1f MB".formatted(size/(1024.0*1024));
  }
}
package gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import controller.Facts;
import controller.Registry;
import controller.Registry.Entry;
import controller.Registry.Kind;
import utils.Range;

/// The grid of registered projects: one tile per project, its icon badged with its state.
@SuppressWarnings("serial")
public final class Tiles extends JPanel{
  public enum State{
    codeInvalid, dataInvalid, idle, dataReadOnly, dataReadWrite, codeNoCache, codeOutdated, codeCompiled, codeRunning;
    Icons.Mark mark(){ return switch(this){
      case codeInvalid, dataInvalid -> Icons.Mark.invalid;
      case codeRunning -> Icons.Mark.running;
      case codeNoCache, codeOutdated -> Icons.Mark.attention;
      case idle -> Icons.Mark.idle;
      case dataReadOnly -> Icons.Mark.dataReadOnly;
      case dataReadWrite -> Icons.Mark.dataReadWrite;
      case codeCompiled -> Icons.Mark.compiled;
    };}
    String text(){ return switch(this){
      case codeInvalid -> "code: invalid content";
      case dataInvalid -> "data: invalid content";
      case idle -> "idle";
      case dataReadOnly -> "data: read only";
      case dataReadWrite -> "data: read write";
      case codeNoCache -> "code: not compiled (no cache)";
      case codeOutdated -> "code: not compiled (cache out of date)";
      case codeCompiled -> "code: compiled";
      case codeRunning -> "code: compiled and running";
    };}
    static State of(Kind kind, boolean valid, boolean hasCache, boolean cacheUpToDate, boolean running){
      if (running){ return codeRunning; }
      if (!valid){ return kind == Kind.code ? codeInvalid : dataInvalid; }
      return switch(kind){
        case idle -> idle;
        case dataReadOnly -> dataReadOnly;
        case dataReadWrite -> dataReadWrite;
        case code -> !hasCache ? codeNoCache : !cacheUpToDate ? codeOutdated : codeCompiled;
      };
    }
  }
  public record Row(Entry entry, Icon icon, long modified, State state){}
  public enum Sort{
    Name, Modified, Compiled, Run;
    Comparator<Row> comparator(){ return switch(this){
      case Name -> Comparator.comparing(r->r.entry().alias(),String.CASE_INSENSITIVE_ORDER);
      case Modified -> Comparator.comparingLong(Row::modified).reversed();
      case Compiled -> Comparator.<Row>comparingLong(r->r.entry().compiled()).reversed();
      case Run -> Comparator.<Row>comparingLong(r->r.entry().run()).reversed();
    };}
  }
  private static final int iconSize= 48;
  private final Registry registry;
  private final Predicate<Path> isRunning;
  private final Consumer<Optional<Path>> onOpen;
  private final DefaultListModel<Row> model= new DefaultListModel<>();
  final JList<Row> list= new JList<>(model);
  private final JComboBox<Sort> sort= new JComboBox<>(Sort.values());
  private final Timer spinner= new Timer(80,_->list.repaint());
  public Tiles(Registry registry, Predicate<Path> isRunning, Consumer<Optional<Path>> onOpen){
    super(new BorderLayout());
    this.registry= registry;
    this.isRunning= isRunning;
    this.onOpen= onOpen;
    list.setName("tiles");
    list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    list.setVisibleRowCount(-1);
    list.setFixedCellWidth(128);
    list.setFixedCellHeight(88);
    list.setCellRenderer(new Tile());
    list.addMouseListener(new MouseAdapter(){
      @Override public void mouseClicked(MouseEvent e){ open(e.getPoint()); }
    });
    sort.setName("sort");
    sort.addActionListener(_->refresh());
    var top= new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Order by"));
    top.add(sort);
    add(top,BorderLayout.NORTH);
    add(new JScrollPane(list),BorderLayout.CENTER);
    refresh();
  }
  public void refresh(){
    var rows= registry.all().stream().map(this::row).sorted(((Sort)sort.getSelectedItem()).comparator()).toList();
    model.clear();
    rows.forEach(model::addElement);
    syncSpinner();
  }
  public void updateFreshness(Path folder, long modified, boolean upToDate){
    for(int i : Range.of(0,model.size())){
      var row= model.get(i);
      if (!row.entry().path().equals(folder)){ continue; }
      if (row.state() == State.codeInvalid || row.state() == State.dataInvalid){ return; }
      var e= row.entry();
      var updated= build(e,modified,State.of(e.kind(),true,Facts.hasCache(folder),upToDate,isRunning.test(folder)));
      if (updated.state() == row.state() && updated.modified() == row.modified()){ return; }
      model.set(i,updated);
      syncSpinner();
      return;
    }
  }
  private void syncSpinner(){
    var anyRunning= IntStream.range(0,model.size()).mapToObj(model::get).anyMatch(r->r.state() == State.codeRunning);
    if (anyRunning && !spinner.isRunning()){ spinner.start(); }
    if (!anyRunning && spinner.isRunning()){ spinner.stop(); }
  }
  public void sortBy(Sort order){ sort.setSelectedItem(order); }
  public void select(Path folder){
    for(int i : Range.of(0,model.size())){
      if (!model.get(i).entry().path().equals(folder)){ continue; }
      list.setSelectedIndex(i);
      list.ensureIndexIsVisible(i);
      onOpen.accept(Optional.of(folder));
      return;
    }
  }
  private Row row(Entry e){
    var facts= Facts.of(e.path(),e.kind());
    var valid= facts.valid() && registry.linkProblem(e).isEmpty() && controller.Names.markerProblem(e.path(),e.alias()).isEmpty();
    return build(e,facts.modified(),State.of(e.kind(),valid,facts.hasCache(),facts.cacheUpToDate(),isRunning.test(e.path())));
  }
  private static Row build(Entry e, long modified, State state){
    return new Row(e,new Icons.Badge(Icons.folder(e.path(),iconSize),iconSize,state.mark()),modified,state);
  }
  private void open(Point p){
    var i= list.locationToIndex(p);
    if (i < 0 || !list.getCellBounds(i,i).contains(p)){ list.clearSelection(); onOpen.accept(Optional.empty()); return; }
    onOpen.accept(Optional.of(model.get(i).entry().path()));
  }
  private static final class Tile extends DefaultListCellRenderer{
    @Override public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean selected, boolean focus){
      var res= (JLabel)super.getListCellRendererComponent(l,value,i,selected,focus);
      var row= (Row)value;
      res.setText(row.entry().alias());
      res.setIcon(row.icon());
      res.setHorizontalAlignment(CENTER);
      res.setHorizontalTextPosition(CENTER);
      res.setVerticalTextPosition(BOTTOM);
      res.setToolTipText(row.entry().path()+" - "+row.state().text());
      return res;
    }
  }
}
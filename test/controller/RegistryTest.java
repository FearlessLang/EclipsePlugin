package controller;

import static controller.Errs.err;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import controller.Registry.Entry;
import controller.Registry.Kind;
import tools.Fs;
import userMessages.UserError;

final class RegistryTest{
  private static final URI uri= URI.create("test:projects.info");
  private static final String root= Path.of("").toAbsolutePath().getRoot().toString().replace('\\','/');
  private static List<Entry> parse(String text){ return Registry.fromInfo(text,Info.parse(text,uri)); }
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  private static String unix(Path p){ return p.toAbsolutePath().normalize().toString().replace('\\','/'); }

  //-- the schema
  @Test void anEmptyFileIsNoProjects(){ assertEquals(List.of(),parse("{}")); }
  @Test void aMinimalProjectDefaultsToIdleWithNoMainsReadsOrEdits(){
    var e= parse("{\"someproject\":{\"path\":\""+root+"abs/someproject\"}}").getFirst();
    assertEquals(new Entry("someproject",Path.of(root+"abs/someproject"),Kind.idle,List.of(),Map.of(),Map.of(),-1,-1),e);
  }
  @Test void aFullCodeProjectParsesEveryField(){
    var e= parse("""
      {"someproject":{
        "path":"%sabs/someproject",
        "kind":"code",
        "mains":["some.Main1","some.Main2"],
        "reads":{"publicfiles":["Data1"]},
        "edits":{"publicdata":["Data2","Data3"]}
      }}""".formatted(root)).getFirst();
    assertEquals(new Entry("someproject",Path.of(root+"abs/someproject"),Kind.code,
      List.of("some.Main1","some.Main2"),Map.of("publicfiles",List.of("Data1")),Map.of("publicdata",List.of("Data2","Data3")),-1,-1),e);
  }
  @Test void allFourKindsParse(){
    assertEquals(Kind.idle,parse("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"idle\"}}").getFirst().kind());
    assertEquals(Kind.code,parse("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"code\"}}").getFirst().kind());
    assertEquals(Kind.dataReadOnly,parse("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"data:readOnly\"}}").getFirst().kind());
    assertEquals(Kind.dataReadWrite,parse("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"data:readWrite\"}}").getFirst().kind());
  }
  @Test void theWholeFileMustBeAnObject(){
    err("[###]The whole file must be an object {...} mapping each project name to the metadata of that project.[###]",()->parse("\"not an object\""));
  }
  @Test void aProjectNameMustBeShapedLikeAFolderName(){
    err("[###]\"Not Valid\" is not a valid project name[###]",()->parse("{\"Not Valid\":{\"path\":\""+root+"a\"}}"));
  }
  @Test void anUnknownAttributeIsRejected(){
    err("[###]Unknown project attribute \"bogus\": the attributes of a project are \"path\", \"kind\", \"mains\", \"reads\", \"edits\".[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"bogus\":\"1\"}}"));
  }
  @Test void aMissingPathIsRejected(){
    err("[###]Project \"a\" is missing its \"path\"[###]",()->parse("{\"a\":{\"kind\":\"idle\"}}"));
  }
  @Test void aRelativePathIsRejected(){
    err("[###]\"path\" must be an absolute path[###]",()->parse("{\"a\":{\"path\":\"relative/path\"}}"));
  }
  @Test void anUnknownKindIsRejectedWithTheFourValidOptionsListed(){
    err("[###]\"kind\" must be one of \"idle\", \"code\", \"data:readOnly\" or \"data:readWrite\", not \"nonsense\".[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"nonsense\"}}"));
  }
  @Test void repeatedMainsAreRejected(){
    err("[###]\"a.Main1\" is repeated in \"mains\".[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"a.Main1\",\"a.Main1\"]}}"));
  }
  @Test void aMainMustBePackageThenType(){
    err("[###]is not a Fearless main name[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"Hello1\"]}}"));
    err("[###]is not a Fearless main name[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"Hello.Hello1\"]}}"));
    err("[###]is not a Fearless main name[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"hello.hello1\"]}}"));
  }
  @Test void mainsAreThePackageQualifiedNamesTheCompilerReports(){
    assertEquals(List.of("hello.Hello1","hello.Hello3"),parse("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"hello.Hello1\",\"hello.Hello3\"]}}").getFirst().mains());
  }
  @Test void aReadsTargetTakesTypeNamesNotPackageQualifiedNames(){
    err("[###]is not a Fearless type name[###]",()->parse("{\"a\":{\"path\":\""+root+"a\",\"reads\":{\"b\":[\"hello.Data1\"]}}}"));
  }
  @Test void identicalOrNestedPathsAreRejected(){
    err("[###]\"b\" has the same path as \"a\", or one is inside the other[###]",()->parse("{\"a\":{\"path\":\""+root+"same\"},\"b\":{\"path\":\""+root+"same\"}}"));
    err("[###]\"b\" has the same path as \"a\", or one is inside the other[###]",()->parse("{\"a\":{\"path\":\""+root+"parent\"},\"b\":{\"path\":\""+root+"parent/child\"}}"));
  }
  @Test void toInfoThenFromInfoRoundTripsAnEntry(){
    var e= new Entry("someproject",Path.of(root+"abs/someproject"),Kind.code,List.of("some.Main1"),Map.of("publicfiles",List.of("Data1")),Map.of(),999,999);
    var back= parse(Info.print(Registry.toInfo(List.of(e)))).getFirst();
    assertEquals(new Entry("someproject",Path.of(root+"abs/someproject"),Kind.code,List.of("some.Main1"),Map.of("publicfiles",List.of("Data1")),Map.of(),-1,-1),back);
  }

  //-- persistence
  @Test void noFileYetIsNoRegisteredFolder(@TempDir Path dir){ assertEquals(List.of(),new Registry(dir).all()); }
  @Test void addedFolderIsIdleWithNoTimesAndNoLinks(@TempDir Path dir){
    var project= folder(dir,"someproject");
    new Registry(dir).add("someproject",project);
    assertEquals(List.of(new Entry("someproject",project.toAbsolutePath().normalize(),Kind.idle,List.of(),Map.of(),Map.of(),-1,-1)),new Registry(dir).all());
  }
  @Test void addingTheSameFolderTwiceRegistersItOnce(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var r= new Registry(dir);
    r.add("someproject",project);
    r.add("again",project.resolve("..").resolve("someproject"));
    assertEquals(1,new Registry(dir).all().size());
  }
  @Test void compileRunTimesAndMainsSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var r= new Registry(dir);
    r.add("someproject",project);
    r.compiled(project,111);
    r.ran(project,222);
    r.update(project,e->e.withMains(List.of("hello.Hello1")));
    var reread= new Registry(dir).all().getFirst();
    assertEquals(111,reread.compiled());
    assertEquals(222,reread.run());
    assertEquals(List.of("hello.Hello1"),reread.mains());
  }
  @Test void forgettingAFolderRemovesOnlyThatOne(@TempDir Path dir){
    var kept= folder(dir,"kept");
    var gone= folder(dir,"gone");
    var r= new Registry(dir);
    r.add("kept",kept);
    r.add("gone",gone);
    r.remove(gone);
    assertEquals(List.of(kept.toAbsolutePath().normalize()),r.all().stream().map(Entry::path).toList());
    assertFalse(r.has(gone));
  }
  @Test void aFolderInsideOrAroundARegisteredOneIsFound(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var r= new Registry(dir);
    r.add("someproject",project);
    assertEquals(project,r.overlapping(project.resolve("inside")).orElseThrow());
    assertEquals(project,r.overlapping(dir).orElseThrow());
    assertEquals(Optional.empty(),r.overlapping(folder(dir,"otherproject")));
    assertEquals(Optional.empty(),r.overlapping(project));
  }
  @Test void aSiblingNamedLikeAPrefixIsNotNested(@TempDir Path dir){
    var r= new Registry(dir);
    r.add("some",folder(dir,"some"));
    assertEquals(Optional.empty(),r.overlapping(folder(dir,"someproject")));
  }
  @Test void awkwardFolderNamesSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"a name with spaces");
    new Registry(dir).add("spacey",project);
    assertEquals(project.toAbsolutePath().normalize(),new Registry(dir).all().getFirst().path());
  }
  @Test void aCorruptedFileRefusesToLoadWithARichError(@TempDir Path dir){
    Fs.writeUtf8(dir.resolve("projects.info"),"not info at all");
    err("[###]Expected a string \"...\", a list [...] or an object {...} here.[###]",()->new Registry(dir).all());
  }
  @Test void commitWritesAValidatedFileAndRejectsLeavingDiskUnchanged(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var r= new Registry(dir);
    r.commit("{\n  \"someproject\": {\"path\": \""+unix(project)+"\", \"kind\": \"code\"}\n}\n");
    assertEquals(Kind.code,r.all().getFirst().kind());
    var before= r.text();
    assertThrows(UserError.class,()->r.commit("{\n  \"someproject\": {\"path\": \""+unix(project)+"\", \"kind\": \"nonsense\"}\n}\n"));
    assertEquals(before,r.text());
  }
  @Test void setLinksSurvivesAReReadAndKeepsReadsAndEditsIndependent(@TempDir Path dir){
    var code= folder(dir,"mycode");
    var pub= folder(dir,"pub");
    var r= new Registry(dir);
    r.add("mycode",code);
    r.add("pub",pub);
    r.update(code,e->e.withKind(Kind.code));
    r.update(pub,e->e.withKind(Kind.dataReadWrite));
    r.update(code,e->e.withLinks(Map.of("pub",List.of("Data1","Data2")),Map.of("pub",List.of("Data1"))));
    var reread= new Registry(dir).of(code).orElseThrow();
    assertEquals(Map.of("pub",List.of("Data1","Data2")),reread.reads());
    assertEquals(Map.of("pub",List.of("Data1")),reread.edits());
  }

  //-- link checks
  private static Entry link(String alias, Path path, Kind kind, Map<String,List<String>> reads, Map<String,List<String>> edits){
    return new Entry(alias,path,kind,List.of(),reads,edits,-1,-1);
  }
  private static Path readme(Path dir, String name){
    var res= dir.resolve(name);
    Fs.writeUtf8(res.resolve("readme"),"hi\n");
    return res;
  }
  private Optional<String> linkProblem(Path dir, Entry e, Entry... others){
    var r= new Registry(dir);
    for (var o: others){ r.add(o.alias(),o.path()); r.update(o.path(),_->o); }
    r.add(e.alias(),e.path());
    r.update(e.path(),_->e);
    return r.linkProblem(e);
  }
  @Test void aNonCodeEntryNeverHasALinkProblem(@TempDir Path dir){
    var e= link("a",readme(dir,"a"),Kind.idle,Map.of("missing",List.of("X")),Map.of());
    assertEquals(Optional.empty(),linkProblem(dir,e));
  }
  @Test void readingARegisteredReadOnlyAndEditingAReadWriteAreFine(@TempDir Path dir){
    var pub= link("pub",readme(dir,"pub"),Kind.dataReadWrite,Map.of(),Map.of());
    var code= link("code",readme(dir,"code"),Kind.code,Map.of("pub",List.of("Data1")),Map.of("pub",List.of("Data1")));
    assertEquals(Optional.empty(),linkProblem(dir,code,pub));
  }
  @Test void readingFromAMissingAliasIsADeadLink(@TempDir Path dir){
    var code= link("code",readme(dir,"code"),Kind.code,Map.of("nosuchproject",List.of("Data1")),Map.of());
    assertTrue(linkProblem(dir,code).orElseThrow().contains("no project called \"nosuchproject\" is registered"));
  }
  @Test void editingAReadOnlyDataProjectIsRejected(@TempDir Path dir){
    var pub= link("pub",readme(dir,"pub"),Kind.dataReadOnly,Map.of(),Map.of());
    var code= link("code",readme(dir,"code"),Kind.code,Map.of(),Map.of("pub",List.of("Data1")));
    assertTrue(linkProblem(dir,code,pub).orElseThrow().contains("accepts only \"data:readWrite\""));
  }
  @Test void readingFromACodeProjectIsRejected(@TempDir Path dir){
    var other= link("other",readme(dir,"other"),Kind.code,Map.of(),Map.of());
    var code= link("code",readme(dir,"code"),Kind.code,Map.of("other",List.of("Data1")),Map.of());
    assertTrue(linkProblem(dir,code,other).orElseThrow().contains("accepts only \"data:readOnly\" or \"data:readWrite\""));
  }
  @Test void readingFromAStructurallyInvalidDataProjectIsADeadLink(@TempDir Path dir){
    var pub= dir.resolve("pub");
    Fs.writeUtf8(pub.resolve("Bad Name.txt"),"hi\n");
    var pubEntry= link("pub",pub,Kind.dataReadOnly,Map.of(),Map.of());
    var code= link("code",readme(dir,"code"),Kind.code,Map.of("pub",List.of("Data1")),Map.of());
    assertTrue(linkProblem(dir,code,pubEntry).orElseThrow().contains("which is itself invalid"));
  }
}
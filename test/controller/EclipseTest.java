package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import controller.Registry.Entry;
import controller.Registry.Kind;
import tools.Fs;

final class EclipseTest{
  private static Entry entry(String alias, Path path){ return new Entry(alias,path,Kind.code,List.of(),Map.of(),Map.of(),-1,-1); }
  private static final String sourceError= """
In file: fear:/_pkb/_rank_app200.fear

002| B:{.text:Str->a.C.text;}
   |               ^^^^^^^^^^

While inspecting a type name
Package "nonexistentpkg" does not exist.
Visible packages: "base".
Error 7 WellFormedness
""";
  @Test void everyKnownProjectIsPublishedAsItsAliasThenItsPathAndReportsSitByAlias(@TempDir Path dir){
    var eclipse= new Eclipse(dir.resolve("eclipse"));
    var one= dir.resolve("one");
    var two= dir.resolve("with space");
    eclipse.publish(List.of(entry("one",one),entry("two",two)));
    assertEquals("one "+one+"\ntwo "+two+"\n",Fs.readUtf8(dir.resolve("eclipse").resolve("projects.txt")));
    assertEquals(dir.resolve("eclipse").resolve("two"),eclipse.reports("two"));
  }
  @Test void publishingAgainReplacesTheListing(@TempDir Path dir){
    var eclipse= new Eclipse(dir.resolve("eclipse"));
    eclipse.publish(List.of(entry("one",dir.resolve("one"))));
    eclipse.publish(List.of());
    assertEquals("",Fs.readUtf8(dir.resolve("eclipse").resolve("projects.txt")));
  }
  @Test void aSourceErrorBecomesItsPathItsLineThenTheWholeMessage(@TempDir Path project){
    Eclipse.problems(project,sourceError);
    assertEquals("_pkb/_rank_app200.fear\n002\n"+sourceError,Fs.readUtf8(project.resolve("problems.txt")));
  }
  @Test void aCompileThatSucceedsClearsTheProblem(@TempDir Path project){
    Eclipse.problems(project,sourceError);
    Eclipse.problems(project,"");
    assertEquals("",Fs.readUtf8(project.resolve("problems.txt")));
  }
  @Test void anErrorWithNoSourcePositionMarksNothing(@TempDir Path project){
    Eclipse.problems(project,"The fearless project folder contains no *.fear files\n");
    assertEquals("",Fs.readUtf8(project.resolve("problems.txt")));
  }
  @Test void aPositionQuotedInsideALongerErrorIsStillFound(@TempDir Path project){
    var wrapped= "Broken reference in a doc comment.\n\n"+sourceError;
    Eclipse.problems(project,wrapped);
    assertEquals("_pkb/_rank_app200.fear\n002\n"+wrapped,Fs.readUtf8(project.resolve("problems.txt")));
  }
}
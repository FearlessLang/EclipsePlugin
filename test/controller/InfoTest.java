package controller;

import static controller.Errs.err;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

final class InfoTest{
  private static final URI uri= URI.create("test:projects.info");
  private static Info parse(String text){ return Info.parse(text,uri); }
  @Test void anEmptyObjectParsesToNoFields(){
    assertEquals(List.of(),((Info.Obj)parse("{}")).fields());
  }
  @Test void aStringParsesToItsValue(){
    assertEquals("hello",((Info.Str)parse("\"hello\"")).value());
  }
  @Test void escapesRoundTrip(){
    var s= (Info.Str)parse("\"a\\\"b\\\\c\\nd\"");
    assertEquals("a\"b\\c\nd",s.value());
    assertEquals("\"a\\\"b\\\\c\\nd\"",Info.print(s).strip());
  }
  @Test void nestedListsAndObjectsParse(){
    var obj= (Info.Obj)parse("{\"a\":[\"x\",\"y\"],\"b\":{}}");
    assertEquals(2,obj.fields().size());
    assertEquals(List.of("x","y"),((Info.Lst)obj.field("a").orElseThrow().value()).items().stream().map(i->((Info.Str)i).value()).toList());
    assertTrue(((Info.Obj)obj.field("b").orElseThrow().value()).fields().isEmpty());
  }
  @Test void lineCommentsAreSkipped(){
    var obj= (Info.Obj)parse("{\n  \"a\": \"x\" // a comment\n}");
    assertEquals("x",((Info.Str)obj.field("a").orElseThrow().value()).value());
  }
  @Test void wholeWhitespaceAndCommentsRoundTripToTheSameStructure(){
    var a= parse("{\"a\":\"1\",\"b\":[\"2\",\"3\"]}");
    var b= parse("{\n  \"a\" : \"1\" ,\n  \"b\" : [ \"2\" , \"3\" ]\n}\n// trailing comment is not even reached\n");
    assertEquals(Info.print(a),Info.print(b));
  }
  @Test void anUnclosedStringPointsAtWhereItStarted(){
    err("[###]never closed with a matching \".[###]",()->parse("{\"a\": \"never closed"));
  }
  @Test void aMissingColonIsReported(){
    err("[###]Expected ':' after the key \"a\".[###]",()->parse("{\"a\" \"x\"}"));
  }
  @Test void aDuplicateKeyIsReported(){
    err("[###]Duplicate key \"a\": this object already has this key.[###]",()->parse("{\"a\":\"1\",\"a\":\"2\"}"));
  }
  @Test void aRawNewlineInAStringIsRejected(){
    err("[###]cannot contain a raw newline; write \\n instead.[###]",()->parse("\"a\nb\""));
  }
  @Test void anUnsafeCharacterIsRejected(){
    err("[###]outside the safe character set of Fearless[###]",()->parse("\"a\tb\""));
  }
  @Test void trailingJunkIsRejected(){
    err("[###]Unexpected extra text after the end of the value[###]",()->parse("\"a\" \"b\""));
  }
  @Test void anUnclosedObjectIsReported(){
    err("[###]never closed with a matching }.[###]",()->parse("{\"a\":\"1\""));
  }
  @Test void anUnclosedListIsReported(){
    err("[###]never closed with a matching ].[###]",()->parse("[\"a\""));
  }
}
package controller;

import org.junit.jupiter.api.Assertions;
import org.opentest4j.AssertionFailedError;

import userMessages.UserError;

/// Shared test helpers: utils.Err set up once, and a matcher for the message of a UserError.
final class Errs{
  private Errs(){}
  static{ utils.Err.setUp(AssertionFailedError.class,Assertions::assertEquals,Assertions::assertTrue); }
  static void err(String expected, Runnable body){
    var e= Assertions.assertThrows(UserError.class,body::run);
    utils.Err.strCmp(expected,e.getMessage());
  }
}
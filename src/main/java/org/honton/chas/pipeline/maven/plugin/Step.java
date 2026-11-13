package org.honton.chas.pipeline.maven.plugin;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Single step in pipeline */
@Getter
@Setter
public class Step {
  /** Name of step. Defaults to cmd value. */
  private String name;

  /** Program to run. */
  private String cmd;

  /** Arguments for program */
  private List<String> args;

  /** Environment variables for program */
  private Map<String, String> env;

  /** Working directory of step */
  private String dir;

  /** Error output file for step. Relative to working dir of step. */
  private String stdErr;

  /**
   * Maven log level for copy of stdErr, ineffective when stdErr is a tty. one of ERROR, WARN, INFO,
   * DEBUG; defaults to INFO
   */
  private String stdErrLevel;

  /**
   * Comma separated positive integers. Step is considered successful if command exit code is one of
   * the success codes. Empty success is equivalent to all exit codes are successful
   */
  private String success;

  /** Maven log level for log exit code. One of ERROR, WARN, INFO, DEBUG; defaults to INFO */
  private String exitLogLevel;

  /**
   * Action to take on command completion. One of LOG_MISMATCH, LOG_ALWAYS, EXIT_MISMATCH; defaults
   * to LOG_MISMATCH
   */
  private String endAction;
}

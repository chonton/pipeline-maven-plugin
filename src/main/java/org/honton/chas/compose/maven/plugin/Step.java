package org.honton.chas.compose.maven.plugin;

import java.util.List;
import java.util.Map;
import lombok.Data;

/** Single step in pipeline */
@Data
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
   * Comma separated positive integers. Step is considered successful if command exit code is one of
   * the success codes. Empty success is equivalent to all exit codes are successful
   */
  private String success;
}

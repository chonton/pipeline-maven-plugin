package org.honton.chas.pipeline.maven.plugin;

import java.io.File;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "run", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class RunPipeline extends AbstractMojo {
  /** Skip pipeline goal */
  @Parameter(property = "pipeline.skip", defaultValue = "false")
  boolean skip;

  /** Pipeline steps */
  @Parameter(required = true)
  List<Step> steps;

  /** Working directory of pipeline */
  @Parameter String dir;

  /** Optional input file to pipeline. Defaults to empty stream. */
  @Parameter String stdIn;

  /** Optional output file for pipeline. Defaults to maven logger. */
  @Parameter String stdOut;

  /**
   * Duration to wait for completion of pipeline. Duration is positive integer followed by unit
   * indicator. Unit can be 'h' (hours), 'm' (minutes), or 's' (seconds).
   */
  @Parameter(defaultValue = "5m")
  String wait;

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  File baseDir;

  public final void execute() throws MojoFailureException, MojoExecutionException {
    if (skip) {
      getLog().info("skipping pipeline");
    } else {
      try {
        new Pipeline(this).run(getLog()::info);
      } catch (RuntimeException | MojoExecutionException e) {
        throw e;
      } catch (Exception e) {
        throw new MojoFailureException(e.getMessage(), e);
      }
    }
  }
}

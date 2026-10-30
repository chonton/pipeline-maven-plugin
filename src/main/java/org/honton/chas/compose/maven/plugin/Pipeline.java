package org.honton.chas.compose.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.apache.maven.plugin.MojoExecutionException;

public class Pipeline {
  private final long deadline;
  private final List<StepProcess> stepProcesses;

  Pipeline(RunPipeline runPipeline) throws MojoExecutionException, IOException {
    Path workDir = setWorkingDir(runPipeline.buildDir, runPipeline.dir);
    stepProcesses = createStepProcesses(workDir, runPipeline.steps);
    deadline = parseWait(runPipeline.wait.trim()).toMillis() + System.currentTimeMillis();

    plumbStdIn(runPipeline.stdIn);
    plumbStdOut(runPipeline.stdOut);
    plumbStdErrs();
  }

  private static Duration parseWait(String wait) throws MojoExecutionException {
    if (wait == null || wait.isEmpty()) {
      return Duration.ofMinutes(5);
    }
    int end = wait.length() - 1;
    int amount = Integer.parseInt(wait.substring(0, end));
    if (amount <= 0) {
      throw new MojoExecutionException("wait must be greater than zero");
    }
    char unit = wait.charAt(end);
    return switch (unit) {
      case 's' -> Duration.ofSeconds(amount);
      case 'm' -> Duration.ofMinutes(amount);
      case 'h' -> Duration.ofHours(amount);
      default -> throw new MojoExecutionException("unknown wait unit " + unit);
    };
  }

  private static Path setWorkingDir(File buildDir, String dir) throws IOException {
    Path buildPath = buildDir.toPath();
    if (dir != null && !dir.isEmpty()) {
      buildPath = buildPath.resolve(dir);
    }
    Files.createDirectories(buildPath);
    return buildPath;
  }

  private static List<StepProcess> createStepProcesses(Path workDir, Step[] steps)
      throws IOException, MojoExecutionException {
    if (steps.length == 0) {
      throw new MojoExecutionException("steps must be specified");
    }
    List<StepProcess> stepProcesses = new ArrayList<>(steps.length);
    for (Step step : steps) {
      stepProcesses.add(new StepProcess(workDir, step));
    }
    return stepProcesses;
  }

  private void plumbStdIn(String stdIn) throws MojoExecutionException {
    stepProcesses.getFirst().plumbStdIn(stdIn);
  }

  private void plumbStdOut(String stdOut) throws IOException {
    stepProcesses.getLast().plumbStdOut(stdOut);
  }

  private void plumbStdErrs() throws IOException {
    boolean isTTY = System.console() != null;
    for (StepProcess stepProcess : stepProcesses) {
      stepProcess.plumbStdErr(isTTY);
    }
  }

  private List<Process> startProcesses() throws IOException {
    List<ProcessBuilder> processes = new ArrayList<>();
    for (StepProcess stepProcess : stepProcesses) {
      processes.add(stepProcess.processBuilder);
    }
    return ProcessBuilder.startPipeline(processes);
  }

  void run(Consumer<String> consumer) throws MojoExecutionException, IOException {
    List<Process> processes = startProcesses();
    setProcesses(processes.iterator());

    pumpStdErrs(consumer);
    pumpStdOut(consumer);
    closeStdIn();
    waitForProcesses();
  }

  private void setProcesses(Iterator<Process> processes) {
    for (StepProcess stepProcess : stepProcesses) {
      stepProcess.setProcess(processes.next());
    }
  }

  private void pumpStdErrs(Consumer<String> consumer) {
    for (StepProcess stepProcess : stepProcesses) {
      stepProcess.pumpStdErr(consumer);
    }
  }

  private void closeStdIn() throws IOException {
    stepProcesses.getFirst().closeStdIn();
  }

  private void pumpStdOut(Consumer<String> consumer) {
    stepProcesses.getLast().pumpStdOut(consumer);
  }

  private void waitForProcesses() throws MojoExecutionException {
    for (StepProcess stepProcess : stepProcesses) {
      try {
        stepProcess.waitForProcess(deadline);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MojoExecutionException(e);
      }
    }
  }
}

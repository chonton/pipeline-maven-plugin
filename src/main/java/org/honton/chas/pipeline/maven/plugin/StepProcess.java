package org.honton.chas.pipeline.maven.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.plugin.MojoExecutionException;

class StepProcess {

  @Getter private final ProcessBuilder processBuilder;
  private final List<Integer> successCodes;
  private final LogLevel logLevel;
  private final EndAction endAction;

  private final Path dir;
  private final Step step;
  private final String name;
  @Setter private Process process;

  StepProcess(Path workDir, Step step) throws IOException, MojoExecutionException {
    successCodes = parseSuccessCodes(step.getSuccess());
    this.logLevel = parseEnum(step.getLogLevel(), LogLevel::values, LogLevel.INFO);
    this.endAction = parseEnum(step.getEndAction(), EndAction::values, EndAction.LOG_MISMATCH);

    dir = setWorkingDir(workDir, step.getDir());
    this.step = step;
    processBuilder = createProcessBuilder(step);
    name = getName(step);
  }

  private static <T extends Enum<T>> T parseEnum(
      String givenValue, Supplier<T[]> values, T defaultValue) throws MojoExecutionException {
    if (givenValue != null) {
      String match = givenValue.trim();
      if (!match.isEmpty()) {
        for (T enumValue : values.get()) {
          if (enumValue.name().equalsIgnoreCase(match)) {
            return enumValue;
          }
        }
        throw new MojoExecutionException("Invalid value " + givenValue);
      }
    }
    return defaultValue;
  }

  private static Path setWorkingDir(Path buildPath, String dir) throws IOException {
    if (dir != null && !dir.isEmpty()) {
      buildPath = buildPath.resolve(dir);
    }
    Files.createDirectories(buildPath);
    return buildPath;
  }

  private static List<Integer> parseSuccessCodes(String success) {
    List<Integer> codes = new ArrayList<>();
    if (success != null) {
      for (String part : success.split("[\\s,]+")) {
        if (!part.isEmpty()) {
          codes.add(Integer.parseInt(part));
        }
      }
    }
    return codes;
  }

  private static String trimCmd(String cmd) throws MojoExecutionException {
    if (cmd != null) {
      cmd = cmd.trim();
      if (!cmd.isEmpty()) {
        return cmd;
      }
    }
    throw new MojoExecutionException("cmd is required");
  }

  private ProcessBuilder createProcessBuilder(Step step) throws MojoExecutionException {
    List<String> command = new ArrayList<>();
    command.add(trimCmd(step.getCmd()));
    List<String> args = step.getArgs();
    if (args != null) {
      command.addAll(args);
    }

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(dir.toFile());
    Map<String, String> env = step.getEnv();
    if (env != null) {
      builder.environment().putAll(env);
    }
    return builder;
  }

  private String getName(Step step) {
    String putative = step.getName();
    if (putative != null) {
      putative = putative.trim();
      if (!putative.isEmpty()) {
        return putative;
      }
    }
    return processBuilder.command().get(0);
  }

  void plumbStdIn(String stdIn) throws MojoExecutionException {
    if (stdIn != null && !stdIn.isEmpty()) {
      Path inputPath = dir.resolve(stdIn);
      if (!Files.exists(inputPath)) {
        throw new MojoExecutionException("Input file does not exist: " + inputPath);
      }
      processBuilder.redirectInput(inputPath.toFile());
    }
  }

  void plumbStdOut(String stdOut) throws IOException {
    if (stdOut != null && !stdOut.isEmpty()) {
      Path outputPath = dir.resolve(stdOut);
      Files.createDirectories(outputPath.getParent());
      processBuilder.redirectOutput(outputPath.toFile());
    }
  }

  void plumbStdErr(boolean isTTY) throws IOException {
    String stdErr = step.getStdErr();
    if (stdErr != null && !stdErr.isEmpty()) {
      Path outputPath = dir.resolve(stdErr);
      Files.createDirectories(outputPath.getParent());
      processBuilder.redirectError(outputPath.toFile());
    } else if (isTTY) {
      processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }
  }

  void closeStdIn() throws IOException {
    OutputStream outputStream = process.getOutputStream();
    if (outputStream != null) {
      outputStream.close();
    }
  }

  void pumpStdErr(BiConsumer<LogLevel, String> logger) {
    new Pump(process.errorReader(), name, logger);
  }

  public void pumpStdOut(BiConsumer<LogLevel, String> logger) {
    new Pump(process.inputReader(), name, logger);
  }

  public void waitForProcess(long deadline, BiConsumer<LogLevel, String> logger)
      throws MojoExecutionException, InterruptedException {
    long waitTime = deadline - System.currentTimeMillis();
    if (waitTime < 0 || !process.waitFor(waitTime, TimeUnit.MILLISECONDS)) {
      throw new MojoExecutionException("Pipeline step " + name + " exceeded wait time");
    }

    int exitCode = process.exitValue();
    boolean misMatch = !successCodes.isEmpty() && !successCodes.contains(exitCode);
    switch (endAction) {
      case EXIT_MISMATCH:
        {
          if (misMatch) {
            throw new MojoExecutionException(
                name + ' ' + exitCode + " does not match success codes");
          }
          break;
        }
      case LOG_ALWAYS:
        {
          logger.accept(logLevel, name + " exited with code " + exitCode);
          break;
        }
      default:
        if (misMatch) {
          logger.accept(logLevel, name + ' ' + exitCode + " does not match success codes");
        }
    }
  }
}

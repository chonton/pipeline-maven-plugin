package org.honton.chas.pipeline.maven.plugin;

import java.io.BufferedReader;
import java.io.Writer;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import lombok.SneakyThrows;

class Pump {

  private final BufferedReader bufferedReader;
  private final String name;
  private final Writer writer;
  private final BiConsumer<LogLevel, CharSequence> logger;
  private final LogLevel logLevel;

  Pump(
      BufferedReader bufferedReader,
      String name,
      Path stdErrDup,
      BiConsumer<LogLevel, CharSequence> logger,
      LogLevel logLevel) {
    this.bufferedReader = bufferedReader;
    this.name = name;
    this.writer = createWriter(stdErrDup);
    this.logger = logger;
    this.logLevel = logLevel;
    ForkJoinPool.commonPool().execute(this::startPumpThread);
  }

  @SneakyThrows
  private static Writer createWriter(Path stdErrDup) {
    return stdErrDup != null
        ? Files.newBufferedWriter(
            stdErrDup, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)
        : null;
  }

  private void startPumpThread() {
    try {
      for (; ; ) {
        String line = bufferedReader.readLine();
        if (line == null) {
          writer.close();
          return;
        }
        if (!line.isEmpty()) {
          if (logLevel != null) {
            logger.accept(logLevel, name + ' ' + line);
          }
          if (writer != null) {
            writer.write(line);
            writer.write(System.lineSeparator());
          }
        }
      }
    } catch (Exception e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}

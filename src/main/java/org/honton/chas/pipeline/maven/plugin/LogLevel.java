package org.honton.chas.pipeline.maven.plugin;

import org.apache.maven.plugin.logging.Log;

public enum LogLevel {
  ERROR {
    @Override
    public void log(Log logger, CharSequence line) {
      logger.error(line);
    }
  },
  WARN {
    @Override
    public void log(Log logger, CharSequence line) {
      logger.warn(line);
    }
  },
  INFO {
    @Override
    public void log(Log logger, CharSequence line) {
      logger.info(line);
    }
  },
  DEBUG {
    @Override
    public void log(Log logger, CharSequence line) {
      logger.debug(line);
    }
  };

  /**
   * Invoke logger at appropriate level
   *
   * @param logger The maven logger
   * @param line The line to log
   */
  public abstract void log(Log logger, CharSequence line);
}

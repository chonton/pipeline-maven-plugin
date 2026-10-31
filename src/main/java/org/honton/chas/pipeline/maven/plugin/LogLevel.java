package org.honton.chas.pipeline.maven.plugin;

import org.apache.maven.plugin.logging.Log;

public enum LogLevel {
  ERROR {
    @Override
    public void log(Log log, String s) {
      log.error(s);
    }
  },
  WARN {
    @Override
    public void log(Log log, String s) {
      log.warn(s);
    }
  },
  INFO {
    @Override
    public void log(Log log, String s) {
      log.info(s);
    }
  },
  DEBUG {
    @Override
    public void log(Log log, String s) {
      log.debug(s);
    }
  },
  NONE;

  public void log(Log log, String s) {}
}

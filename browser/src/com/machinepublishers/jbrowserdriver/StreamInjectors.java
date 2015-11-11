/* 
 * jBrowserDriver (TM)
 * Copyright (C) 2014-2015 Machine Publishers, LLC
 * ops@machinepublishers.com | screenslicer.com | machinepublishers.com
 * Cincinnati, Ohio, USA
 *
 * You can redistribute this program and/or modify it under the terms of the GNU Affero General Public
 * License version 3 as published by the Free Software Foundation.
 *
 * "ScreenSlicer", "jBrowserDriver", "Machine Publishers", and "automatic, zero-config web scraping"
 * are trademarks of Machine Publishers, LLC.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License version 3 for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License version 3 along with this
 * program. If not, see http://www.gnu.org/licenses/
 * 
 * For general details about how to investigate and report license violations, please see
 * https://www.gnu.org/licenses/gpl-violation.html and email the author, ops@machinepublishers.com
 */
package com.machinepublishers.jbrowserdriver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

class StreamInjectors {
  public static interface Injector {
    byte[] inject(HttpURLConnection connection, byte[] inflatedContent, String originalUrl, long settingsId);
  }

  private static final Object lock = new Object();
  private static final List<Injector> injectors = new ArrayList<Injector>();

  static void add(Injector injector) {
    synchronized (lock) {
      injectors.add(injector);
    }
  }

  static void remove(Injector injector) {
    synchronized (lock) {
      injectors.remove(injector);
    }
  }

  static void removeAll() {
    synchronized (lock) {
      injectors.clear();
    }
  }

  static InputStream injectedStream(HttpURLConnection conn,
      String originalUrl, long settingsId) throws IOException {
    if (conn.getErrorStream() != null) {
      return conn.getInputStream();
    }
    byte[] bytes = new byte[0];
    try {
      String encoding = conn.getContentEncoding();
      if ("gzip".equalsIgnoreCase(encoding)) {
        bytes = Util.toBytes(new GZIPInputStream(conn.getInputStream()));
      } else if ("deflate".equalsIgnoreCase(encoding)) {
        bytes = Util.toBytes(new InflaterInputStream(conn.getInputStream()));
      } else {
        bytes = Util.toBytes(conn.getInputStream());
      }
      synchronized (lock) {
        for (Injector injector : injectors) {
          byte[] newContent = injector.inject(conn, bytes, originalUrl, settingsId);
          if (newContent != null) {
            bytes = newContent;
          }
        }
      }
    } catch (Throwable t) {
      Logs.exception(t);
    } finally {
      Util.close(conn.getInputStream());
    }
    return new ByteArrayInputStream(bytes);
  }
}

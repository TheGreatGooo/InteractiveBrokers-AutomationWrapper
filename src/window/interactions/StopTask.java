// This file is part of the "IBController".
// Copyright (C) 2004 Steven M. Kearns (skearns23@yahoo.com )
// Copyright (C) 2004 - 2011 Richard L King (rlking@aultan.com)
// For conditions of distribution and use, see copyright notice in COPYING.txt

// IBController is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// IBController is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with IBController. If not, see <http://www.gnu.org/licenses/>.

package window.interactions;

import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.swing.JFrame;

import api.CommandChannel;
import utils.SwitchLock;
import window.manager.MainWindowManager;

public class StopTask
    implements Runnable {

  private static final SwitchLock _Running = new SwitchLock();

  private final CommandChannel mChannel;
  private final Executor executor;
  private final ScheduledExecutorService scheduledExecutorService;

  public StopTask(final CommandChannel channel, Executor executor, ScheduledExecutorService scheduledExecutorService) {
    mChannel = channel;
    this.executor = executor;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public void run() {
    if (!_Running.set()) {
      writeNack("STOP already in progress");
      return;
    }

    try {
      ((ExecutorService) executor).shutdownNow();
      scheduledExecutorService.shutdownNow();
      System.exit(0);
      writeInfo("Closing IBController");
      stop();
    } catch (Exception ex) {
      writeNack(ex.getMessage());
    }
  }

  public final static boolean shutdownInProgress() {
    return _Running.query();
  }

  private void stop() {
    JFrame jf = MainWindowManager.mainWindowManager().getMainWindow();

    WindowEvent wev = new WindowEvent(jf, WindowEvent.WINDOW_CLOSING);
    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(wev);

    writeAck("Shutting down");
  }

  private void writeAck(String message) {
    if (!(mChannel == null)) mChannel.writeAck(message);
  }

  private void writeInfo(String message) {
    if (!(mChannel == null)) mChannel.writeInfo(message);
  }

  private void writeNack(String message) {
    if (!(mChannel == null)) mChannel.writeNack(message);
  }

}

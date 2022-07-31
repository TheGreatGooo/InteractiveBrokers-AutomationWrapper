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

package window.handlers;

import java.awt.Window;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JLabel;
import io.github.ma1uta.matrix.client.StandaloneClient;
import io.github.ma1uta.matrix.client.model.sync.JoinedRoom;
import io.github.ma1uta.matrix.client.model.sync.Rooms;
import io.github.ma1uta.matrix.client.model.sync.SyncResponse;
import io.github.ma1uta.matrix.client.sync.SyncLoop;
import io.github.ma1uta.matrix.client.sync.SyncParams;
import io.github.ma1uta.matrix.event.Event;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import utils.Settings;
import utils.SwingUtils;
import window.WindowHandler;

public class SecondFactorAuthenticationHandler implements WindowHandler {
  private Instant lastWindowOpened = Instant.MIN;
  private final Pattern codePattern = Pattern.compile(
      "<html>If you did not receive the notification, enter this challenge in the <br>IBKR Mobile App: &nbsp;&nbsp;&nbsp;<strong style='font-size:110%;'>([0-9 ]+)</strong>&nbsp;&nbsp;&nbsp; Then enter the response below and click OK.</html>");
  private final Pattern challengePattern = Pattern.compile("Challenge: ([0-9 ]+)");
  private final StandaloneClient mxClient;
  private ExecutorService executor;
  private final String matrixServerName;
  private final String matrixBotName;
  private JLabel challengeLabel = null;

  public SecondFactorAuthenticationHandler() {
    matrixServerName = Settings.settings().getString("MatrixServerName", "");
    matrixBotName = Settings.settings().getString("MatrixBotName", "");
    if (matrixServerName.isEmpty()){
      return;
    }
    try {
      mxClient =
          new StandaloneClient.Builder().domain(matrixServerName).userId(matrixBotName).build();
      mxClient.auth()
          .login(matrixBotName, Settings.settings().getString("MatrixBotAuth", "").toCharArray());
    } catch (Exception e) {
      System.exit(989);
      throw (e);
    }
  }

  @Override
  public boolean filterEvent(Window window, int eventId) {
    switch (eventId) {
      case WindowEvent.WINDOW_OPENED:
        lastWindowOpened = Instant.now();
        executor = Executors.newFixedThreadPool(1);
        return true;
      case WindowEvent.WINDOW_CLOSED:
        if (Duration.between(lastWindowOpened, Instant.now())
            .compareTo(Duration.ofSeconds(3)) < 0) {
          System.exit(999);
        }
        executor.shutdown();
        break;
    }
    return false;
  }

  @Override
  public void handleWindow(Window window, int eventID) {
    Matcher matcher = getMatcher(window);
    String authCode = matcher.group(1);
    mxClient.room().joinedRooms().getJoinedRooms().stream().forEach(
        room -> mxClient.eventAsync()
            .sendMessage(room, "@room IBApi need 2factor auth code is " + authCode));
    SyncLoop syncLoop = new SyncLoop(
        mxClient.sync(),
        (
            syncResponse,
            syncParams) -> processMatrixMessages(syncResponse, syncParams, window, authCode));
    syncLoop.setInit(SyncParams.builder().fullState(true).timeout(100000L).build());
    executor.execute(syncLoop);
  }

  private Matcher getMatcher(Window window) {
    Matcher matcher;
    if (challengeLabel == null) {
      matcher = codePattern.matcher(
          SwingUtils
              .findLabel(
                  window,
                  "If you did not receive the notification, enter this challenge in the")
              .getText());
    } else {
      matcher = challengePattern.matcher(challengeLabel.getText());
    }
    if (!matcher.matches()) {
      System.exit(998);
    }
    return matcher;
  }

  @Override
  public boolean recogniseWindow(Window window) {
    if (!(window instanceof JDialog))
      return false;

    if (SwingUtils.titleContains(window, "Second Factor Authentication")) {
      return true;
    }
    challengeLabel = SwingUtils.findLabel(window, "Challenge: ");
    if (challengeLabel != null) {
      Matcher matcher = challengePattern.matcher(challengeLabel.getText());
      if (matcher.matches()) {
        return true;
      } else {
        System.out.println("Found challenge but did not match expected pattern.");
      }
    }
    return false;
  }

  private void processMatrixMessages(
      SyncResponse syncResponse,
      SyncParams syncParams,
      Window window,
      String authCode) {
    try {
      System.out.println("Next batch: " + syncParams.getNextBatch());
      if (syncParams.isFullState()) {
        syncParams.setFullState(false);
      }
      Rooms rooms = syncResponse.getRooms();
      if (rooms != null && rooms.getJoin() != null) {
        rooms.getJoin().entrySet().stream().map(Entry::getValue).map(JoinedRoom::getTimeline)
            .map(Optional::ofNullable)
            .map(
                timelineMaybe -> timelineMaybe
                    .flatMap(timeline -> Optional.ofNullable(timeline.getEvents())))
            .filter(Optional::isPresent).map(Optional::get).flatMap(List::stream)
            .map(Event::getContent).filter(RoomMessageContent.class::isInstance)
            .map(RoomMessageContent.class::cast).map(RoomMessageContent::getBody)
            .filter(
                body -> body.startsWith(
                    String.format(
                        "> <@%s:%s> @room IBApi need 2factor auth code is ",
                        matrixBotName.toLowerCase(),
                        matrixServerName.toLowerCase()) + authCode))
            .forEach(replyMessage -> {
              String[] replyParts = replyMessage.split("\n");
              if (replyParts.length == 3) {
                String returnCode = replyParts[2];
                returnCode = returnCode.replace(" ", "");
                SwingUtils.setTextField(window, 0, returnCode.substring(0, 4));
                SwingUtils.setTextField(window, 1, returnCode.substring(4));
                SwingUtils.clickButton(window, "OK");
                syncParams.setTerminate(true);
                Thread.currentThread().interrupt();
              }
            });
      }
    } catch (Exception e) {
      System.exit(808);
    }
  }
}

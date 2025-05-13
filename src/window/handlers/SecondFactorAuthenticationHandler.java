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
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JLabel;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequestFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import utils.Settings;
import utils.SwingUtils;
import window.WindowHandler;

public class SecondFactorAuthenticationHandler implements WindowHandler {
  private Instant lastWindowOpened = Instant.MIN;
  private ExecutorService executor;
  private final String httpMessageBusUrl;
  private final HttpClient httpClient;
  private JLabel challengeLabel = null;
  private final Instant instant = Instant.now();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private Optional<Double> lastMessageTs = Optional.of(0.0);

  public SecondFactorAuthenticationHandler() {
    httpMessageBusUrl = Settings.settings().getString("httpMessageBusUrl", "");
    if (httpMessageBusUrl.isEmpty()){
      httpClient = null;
      return;
    }
    httpClient = HttpClients.createDefault();
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
            .compareTo(Duration.ofSeconds(1)) < 0) {
          System.exit(999);
        }
        executor.shutdown();
        break;
    }
    return false;
  }

  @Override
  public void handleWindow(Window window, int eventID) {
    if (httpClient == null ){
      return;
    }
    lastMessageTs = getLastMessageTs();
    HttpPost httpRequest = new HttpPost(httpMessageBusUrl);
    httpRequest.setEntity(new StringEntity("@room IBApi need TOPT auth code ["+ instant.getEpochSecond()+"]", ContentType.APPLICATION_JSON));
    httpClient.execute(httpRequest);
    executor.execute(()->{
      Instant startTime = Instant.now();
      while (Duration.between(startTime, Instant.now()).toMinutes() < 10) {
        JsonNode rootNode = getMessages();
        int messages = rootNode.size();
        int i = 0;
        while (i < messages) {
          JsonNode message = rootNode.get(i);
          if(lastMessageTs.orElse(0.0) < message.get("message_received_ts").asDouble()){
            String messageString = message.get("message").asText();
            if(messageString.matches("\\d{6}")){
              SwingUtils.setTextField(window, 0, messageString);
              return;
            }
          }
          i++;
        }
        Thread.sleep(1000);
      }
      System.out.println("Giving up waiting for TOTP");
    });
  }

  @Override
  public boolean recogniseWindow(Window window) {
    if (!(window instanceof JDialog))
      return false;

    if (SwingUtils.titleContains(window, "Second Factor Authentication")) {
      return true;
    }
    return false;
  }

  private Optional<Double> getLastMessageTs(){
    JsonNode jsonNode = getMessages();
    int messages = jsonNode.size();
    double lastTs = 0.0;
    int i = 0;
    while( i< messages ){
      JsonNode message = jsonNode.get(i);
      if(lastTs<message.get("message_received_ts").asDouble()){
        lastTs = message.get("message_received_ts").asDouble();
      }
      i++;
    }
    return Optional.of(lastTs);
  }

  private JsonNode getMessages() {
    HttpGet httpGet = new HttpGet(httpMessageBusUrl);
    return httpClient.execute(httpGet,response -> objectMapper.readTree(response.getEntity().getContent()));
  }

}

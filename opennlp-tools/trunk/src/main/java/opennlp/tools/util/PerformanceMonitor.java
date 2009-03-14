/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package opennlp.tools.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The {@link PerformanceMonitor} measures increments to a counter.
 * During the computation it prints out current and average throughput
 * per second. After the computation is done it prints a final performance
 * report.
 * 
 * Note:
 * This class is not thread safe.
 */
public class PerformanceMonitor {

  private ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(1);

  private final String unit;
  
  private ScheduledFuture<?> beeperHandle;
    
  private volatile long startTime;
  
  private volatile int counter;
  
  public PerformanceMonitor(String unit) {
    this.unit = unit;
  }
  
  public void incrementCounter() {
    counter++;
  }
  
  public void startPrinter() {
    
    startTime = System.currentTimeMillis();
    
    final Runnable beeper = new Runnable() {
      
      private long lastTimeStamp = startTime;
      private int lastCount = counter;
      
      public void run() {

        int deltaCount = counter - lastCount;

        long timePassedSinceLastCount = System.currentTimeMillis()
            - lastTimeStamp;

        double currentThroughput;

        if (timePassedSinceLastCount > 0) {
          currentThroughput = deltaCount / ((double) timePassedSinceLastCount / 1000);
        } else {
          currentThroughput = 0;
        }

        long totalTimePassed = System.currentTimeMillis() - startTime;
        
        double averageThroughput;
        if (totalTimePassed > 0) {
          averageThroughput = counter / (((double) totalTimePassed) / 1000);
        }
        else {
          averageThroughput = 0;
        }
        
        System.out.printf("current: %.1f " + unit + "/s avg: %.1f " + unit + "/s total: %d " + unit + "%n", currentThroughput,
            averageThroughput, counter);

        lastTimeStamp = System.currentTimeMillis();
        lastCount = counter;
      }
    }; 
    
   beeperHandle = scheduler.scheduleAtFixedRate(beeper, 1, 1, TimeUnit.SECONDS);
  }
  
  public void stopPrinterAndPrintFinalResult() {
    if (beeperHandle != null) {
      // yeah we have time to finish current
      // printing if there is one
      beeperHandle.cancel(false);
    }
    
    scheduler.shutdown();
    
    long timePassed = System.currentTimeMillis() - startTime;
    
    double average;
    if (timePassed > 0) {
      average = counter / (timePassed / 1000d);
    }
    else {
      average = 0;
    }
    
    System.out.println();
    System.out.println();
    
    System.out.printf("Average: %.1f " + unit +"/s %n", average);
    System.out.println("Total: " + counter + " " + unit);
    System.out.println("Runtime: " + timePassed / 1000d + "s");
  }
}

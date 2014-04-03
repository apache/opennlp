/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.ml.maxent.quasinewton;

/**
 * Class to store lineSearch result
 */
public class LineSearchResult {
  
  private int fctEvalCount;
  private double stepSize;
  private double valueAtCurr;
  private double valueAtNext;
  private double[] gradAtCurr;
  private double[] gradAtNext;
  private double[] currPoint;
  private double[] nextPoint;

  public LineSearchResult(double stepSize, double valueAtCurr, 
      double valueAtNext, double[] gradAtCurr, double[] gradAtNext, 
      double[] currPoint, double[] nextPoint, int fctEvalCount) 
  {
    setAll(stepSize, valueAtCurr, valueAtNext, gradAtCurr, gradAtNext, 
      currPoint, nextPoint, fctEvalCount);
  }

  public void setAll(double stepSize, double valueAtCurr, 
      double valueAtNext, double[] gradAtCurr, double[] gradAtNext, 
      double[] currPoint, double[] nextPoint, int fctEvalCount) 
  {
    this.stepSize     = stepSize;
    this.valueAtCurr  = valueAtCurr;
    this.valueAtNext  = valueAtNext;
    this.gradAtCurr   = gradAtCurr;
    this.gradAtNext   = gradAtNext;
    this.currPoint    = currPoint;
    this.nextPoint    = nextPoint;
    this.fctEvalCount = fctEvalCount;
  }
  
  public double getFuncChangeRate() {
    return (valueAtCurr - valueAtNext) / valueAtCurr;
  }
  
  public double getStepSize() {
    return stepSize;
  }
  public void setStepSize(double stepSize) {
    this.stepSize = stepSize;
  }
  
  public double getValueAtCurr() {
    return valueAtCurr;
  }
  public void setValueAtCurr(double valueAtCurr) {
    this.valueAtCurr = valueAtCurr;
  }
  
  public double getValueAtNext() {
    return valueAtNext;
  }
  public void setValueAtNext(double valueAtNext) {
    this.valueAtNext = valueAtNext;
  }
  
  public double[] getGradAtCurr() {
    return gradAtCurr;
  }
  public void setGradAtCurr(double[] gradAtCurr) {
    this.gradAtCurr = gradAtCurr;
  }
  
  public double[] getGradAtNext() {
    return gradAtNext;
  }
  public void setGradAtNext(double[] gradAtNext) {
    this.gradAtNext = gradAtNext;
  }
  
  public double[] getCurrPoint() {
    return currPoint;
  }
  public void setCurrPoint(double[] currPoint) {
    this.currPoint = currPoint;
  }
  
  public double[] getNextPoint() {
    return nextPoint;
  }
  public void setNextPoint(double[] nextPoint) {
    this.nextPoint = nextPoint;
  }
  
  public int getFctEvalCount() {
    return fctEvalCount;
  }
  public void setFctEvalCount(int fctEvalCount) {
    this.fctEvalCount = fctEvalCount;
  }
  
  public static LineSearchResult getInitialObject(double valueAtX, 
      double[] gradAtX, double[] x, int fctEvalCount) {
    return new LineSearchResult(0.0, 0.0, valueAtX, new double[x.length], gradAtX, 
        new double[x.length], x, fctEvalCount);
  }

  public static LineSearchResult getInitialObject(double valueAtX, double[] gradAtX, double[] x) {
    return new LineSearchResult(0.0, 0.0, valueAtX, new double[x.length], 
        gradAtX, new double[x.length], x, 0);
  }
}
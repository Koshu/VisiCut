/**
 * This file is part of VisiCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.koshu.pathoptimization;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

/**
 *
 * @author Arne Bloem <bloem@uni-bremen.de>
 */
public class kerfCorrection
{
  private double kerfSize;
  private kerfDir kerfDirection; 
    
  public enum kerfDir
  {
    OUTSIDE,
    INSIDE
  }
  
  public kerfCorrection(double kerfSize, kerfDir kerfDirection){
    this.kerfSize = kerfSize;
    this.kerfDirection = kerfDirection;
  }
      
  private double angleOfVector(double x, double y){
    double a = Math.atan2(y,x);
    return a < 0.0 ? (2 * Math.PI) + a : a;
  }
  
  private double[] calculateCorrectedPoint(double x0, double y0, double x1, double y1, double x2, double y2, double off)
  {
    //Move points to zero (x1,y1) 
    double xt0 = x0 - x1;
    double xt2 = x2 - x1;

    double yt0 = y0 - y1;
    double yt2 = y2 - y1;

    //Calculate angles between lines
    double a0 = angleOfVector(xt0,yt0);
    double a1 = angleOfVector(xt2,yt2);
    double ad = a0 - a1 < 0.0 ? (2 * Math.PI) + (a0 - a1) : (a0 - a1);
    
    //Calculate offset at the specific angle
    double vl = -off / Math.cos(ad / 2 - (Math.PI / 2));

    //Calculate new vector to move x1 and y1
    double offX = Math.cos(a1 + ad / 2) * vl;
    double offY = Math.sin(a1 + ad / 2) * vl;

    //Woohoo
    return new double[]{x1 + offX, y1 + offY};
  }
  
  //Find out if we are running clockwise oder counter-clockwise
  private boolean isShapeClockwise(ArrayList<double[]> pointList){
    //Find highest Point
    int highestPoint = 0;
    double[]p1 = pointList.get(0);
    
    for (int i = 0; i < pointList.size(); i++) {
      double[] p = pointList.get(i);
      if(p[1] > p1[1]){
        highestPoint = i;
        p1 = pointList.get(i);
      }
    }
    
    //Get the two neighboring points
    int nextPoint = highestPoint + 1 >= pointList.size() ? 0 : highestPoint + 1;
    int lastPoint = highestPoint - 1 < 0 ? pointList.size() - 1 :highestPoint - 1;
    
    double[]p0 = pointList.get(lastPoint);
    double[]p2 = pointList.get(nextPoint);
    
    //Calculate the angle of the points
    double a0 = angleOfVector(p0[0]-p1[0],p0[1]-p1[1]);
    double a2 = angleOfVector(p2[0]-p1[0],p2[1]-p1[1]);
    
    return a0 < a2;
  }
  
  private ArrayList<double[]> shapeToListAndSannity(Shape sh){
    ArrayList<double[]> pointList = new ArrayList<double[]>();

    //For now beziercurves are flattened to aproximate the offset curve
    //there are more advanced methods but im not sure if they are worth the trouble
    PathIterator iter = sh.getPathIterator(null,0.005);
    
    boolean started = false;
    
    while (!iter.isDone())
    {
      double[] test = new double[8];
      int result = iter.currentSegment(test);

      //We need to start with a moveto. Other lines are not supported yet.
      if(!started){
        started = true;
        if(result != PathIterator.SEG_MOVETO){
          return null;
        }
      } else {
        if(result == PathIterator.SEG_MOVETO){
          return null;
        }
      }
      
      switch (result)
      {
        case PathIterator.SEG_MOVETO:
          pointList.add(new double[]
          {
            test[0], test[1], PathIterator.SEG_MOVETO
          });
          break;
        case PathIterator.SEG_LINETO:
          pointList.add(new double[]
          {
            test[0], test[1], PathIterator.SEG_LINETO
          });
          break;
        case PathIterator.SEG_CLOSE:
          break;
      }

      iter.next();
      
      //The last point needs to be a seg_close. Open paths are not supported yet
      if(iter.isDone() && result != PathIterator.SEG_CLOSE){
        return null;
      }
    }
    
    if(pointList.size() < 3){
      //nope
      return null;
    }
    
    return pointList;
  }
  
  public Shape calculateCorrectedPath(Shape sh)
  {
    ArrayList<double[]> pointList;
    ArrayList<double[]> pointListCorr = new ArrayList<double[]>();

    //Sort every point of the shape in an arraylist, which less awkward to analyse
    pointList = shapeToListAndSannity(sh);
    if(pointList == null) return null;
    
    //Calculate the direction
    double offSet = kerfSize;
    if(kerfDirection != kerfDir.OUTSIDE){
      offSet = -offSet;
    }
    if(isShapeClockwise(pointList)){
      offSet = -offSet;
    }
    
    for (int i = 0; i < pointList.size(); i++)
    {
      double[] p1 = pointList.get(i);

      //Search for points before p1 that is not equal to p1
      double[] p0;
      int itmp = i;
      do
      {
        itmp--;
        if (itmp < 0)
        {
          itmp = pointList.size() - 1;
        }
        p0 = pointList.get(itmp);
      }
      while (p0[0] == p1[0] && p0[1] == p1[1]);

      //Search for points after p1 that is not equal to p1
      double[] p2;
      itmp = i;
      do
      {
        itmp++;
        if (itmp >= pointList.size())
        {
          itmp = 0;
        }
        p2 = pointList.get(itmp);
      }
      while (p2[0] == p1[0] && p2[1] == p1[1]);

      
      double[] res = calculateCorrectedPoint(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], offSet);

      pointListCorr.add(new double[]
      {
        res[0], res[1], p1[2]
      });
    }

    //Transform new path back to a shape object
    Path2D.Double corrShape = new Path2D.Double();

    for (double[] p : pointListCorr)
    {
      switch ((int) p[2])
      {
        case PathIterator.SEG_MOVETO:
          corrShape.moveTo(p[0], p[1]);
          break;
        case PathIterator.SEG_LINETO:
          corrShape.lineTo(p[0], p[1]);
          break;
      }
    }

    corrShape.closePath();

    return corrShape;
  }
}
